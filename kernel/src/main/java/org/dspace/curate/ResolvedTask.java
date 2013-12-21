/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.curate;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.DSpaceObject;
import org.dspace.core.Context;
import org.dspace.core.ConfigurationManager;
import org.dspace.curate.record.Record;
import org.dspace.curate.record.Records;
import org.dspace.curate.record.Recorder;
import org.dspace.eperson.EPerson;

/**
 * ResolvedTask wraps an implementation of one of the CurationTask or
 * ScriptedTask interfaces and provides for uniform invocation based on
 * CurationTask methods.
 *
 * @author richardrodgers
 */
public class ResolvedTask {

    private static Logger log = LoggerFactory.getLogger(ResolvedTask.class);

    private static final String recorderKey = Recorder.class.getName();

    // wrapped objects (one or the other)
    private CurationTask cTask;
    private ScriptedTask sTask;
    // local name of task
    private String taskName;
    // recorder (if any)
    private Recorder recorder = null;
    // annotation data
    private boolean distributive = false;
    private boolean mutative = false;
    private Curator.Invoked mode = null;
    private int[] codes = null;
    // record annotation metadata
    private List<RecordMetadata> recList = null;
    // optional task properties
    private Properties taskProps = null;

    protected ResolvedTask(String taskName, CurationTask cTask) {
        this.taskName = taskName;
        this.cTask = cTask;
        // process annotations
        Class ctClass = cTask.getClass();
        distributive = ctClass.isAnnotationPresent(Distributive.class);
        mutative = ctClass.isAnnotationPresent(Mutative.class);
        Suspendable suspendAnno = (Suspendable)ctClass.getAnnotation(Suspendable.class);
        if (suspendAnno != null) {
            mode = suspendAnno.invoked();
            codes = suspendAnno.statusCodes();
        }
        Record recAnno = (Record)ctClass.getAnnotation(Record.class);
        if (recAnno != null) {
            processRecord(recAnno);
        }
        // maybe multiple messages ?
        Records recsAnno = (Records)ctClass.getAnnotation(Records.class);
        if (recsAnno != null) {
            for (Record rAnno : recsAnno.value()) {
                processRecord(rAnno);
            }
        }
    }

    protected ResolvedTask(String taskName, Program program) {
        this.taskName = taskName;
        this.cTask = program;
        // annotation processing TBD
    }

    protected ResolvedTask(String taskName, ScriptedTask sTask) {
        this.taskName = taskName;
        this.sTask = sTask;
        // annotation processing TBD
    }

    // copy contructor
    protected ResolvedTask(ResolvedTask task) {
        this.taskName = task.getName();
        try {
            if (task.cTask != null) {
                this.cTask = task.cTask.getClass().newInstance();
            } else {
                // will this even work?
                this.sTask = task.sTask.getClass().newInstance();
            }
        } catch (Exception e) {}
    }

    /**
     * Initialize task - parameters inform the task of it's invoking curator.
     * Since the curator can provide services to the task, this represents
     * curation DI.
     * 
     * @param curator the Curator controlling this task
     * @throws IOException
     */
    public void init(Curation curation) throws IOException {
        // any recorder required?
        if (recList != null) {
            recorder = (Recorder)curation.obtainResource(recorderKey);
            if (recorder == null) {
                String recorderClass = ConfigurationManager.getProperty("curate", "recorder.impl");
                if (recorderClass != null) {
                    try {
                        recorder = (Recorder)Class.forName(recorderClass).newInstance();
                        recorder.init();
                        String policy = null;
                        if (recorder instanceof Closeable) {
                            policy = "close";
                        }
                        curation.manageResource(recorderKey, recorder, policy);
                    } catch (Exception e) {
                        log.error("Error instantiating recorder", e);
                        throw new IOException("Missing Recorder");
                    }
                } else {
                    log.error("No recorder configured");
                    throw new IOException("Missing Recorder");
                }
            }
        }
        if (unscripted()) {
            cTask.init(curation, taskName);
        } else {
            sTask.init(curation, taskName);
        }
    }

    /**
     * Perform the curation task upon passed DSO
     *
     * @param dso the DSpace object
     * @return status code
     * @throws AuthorizeException
     * @throws IOException
     * @throws SQLException
     */
    public int perform(DSpaceObject dso) throws AuthorizeException, IOException, SQLException {
        return unscripted() ? cTask.perform(dso) : sTask.performDso(dso);
    }

    /**
     * Perform the curation task for passed id
     * 
     * @param ctx DSpace context object
     * @param id persistent ID for DSpace object
     * @return status code
     * @throws AuthorizeException
     * @throws IOException
     * @throws SQLException
     */
    public int perform(Context ctx, String id) throws AuthorizeException, IOException, SQLException {
        return unscripted() ? cTask.perform(ctx, id) : sTask.performId(ctx, id);
    }

    /**
     * Returns a task property value for a given key (name)
     *
     * @param name the property name
     * @return value the property value
     */
    protected String taskProperty(Context ctx, String name) {
        if (taskProps == null) {
            // load properties
            taskProps = new Properties();
            StringBuilder modName = new StringBuilder();
            for (String segment : taskName.split("\\.")) {
                // load property segments if present
                modName.append(segment);
                Properties modProps = TaskResolver.taskConfig(ctx, modName.toString()); //ConfigurationManager.getProperties(modName.toString());
                if (modProps != null) {
                    taskProps.putAll(modProps);
                }
                modName.append(".");
            }
            // warn if *no* properties found
            if (taskProps.size() == 0) {
                log.warn("Warning: No configuration properties found for task: " + taskName);
            }
        }
        return taskProps.getProperty(name);
    }
    
    /**
     * Handle any record requests
     * 
     * @param objId identifier of DSpace Object
     * @param ctx a DSpace context
     * @param status the status code returned by the task
     * @param result the result string assigned by to task or null if absent
     */
    public void record(String objId, Context ctx, int status, String result) throws IOException {
        if (recorder != null && recList != null) {
            String epId = null;
            if (ctx != null) {
                EPerson eperson = ctx.getCurrentUser();
                epId = (eperson != null) ? eperson.getName() : null;
            }
            long timestamp = System.currentTimeMillis();
            for (RecordMetadata rmd : recList) {
                // is status code among those we respond to?
                if (rmd.recCodes.contains(status)) {
                    recorder.record(timestamp, objId, epId, taskName,
                                    rmd.recType, rmd.recValue, status, result);
                }
            }
        }
    }
    
    /**
     * Returns local name of task
     * @return name
     *         the local name of the task
     */
    public String getName() {
        return taskName;
    }
    
    /**
     * Returns whether task should be distributed through containers
     * @return boolean 
     *         true if the task is distributive
     */
    public boolean isDistributive() {
        return distributive;
    }
    
    /**
     * Returns whether task alters (mutates) it's target objects
     * 
     */
    public boolean isMutative() {
        return mutative;
    }
    
    /**
     * Returns the invocation mode for this task
     *
     */
    public Curator.Invoked getMode() {
        return mode;
    }
    
    public int[] getCodes() {
        return codes;
    }
    
    private void processRecord(Record recAnno) {
        if (recList == null) {
            recList = new ArrayList<RecordMetadata>();
        }
        recList.add(new RecordMetadata(recAnno));
    }
    
    private boolean unscripted() {
        return sTask == null;
    }
    
    private class RecordMetadata {
        public String recType;
        public String recValue;
        public List<Integer> recCodes;
    
        public RecordMetadata(Record rec) {
            recType = rec.type();
            recValue = rec.value();
            recCodes = new ArrayList<Integer>();
            for (int code : rec.statusCodes()) {
                recCodes.add(code);
            }
        }
    }
}
