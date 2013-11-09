/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.curate;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Iterator;
import java.sql.SQLException;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Site;
import org.dspace.core.Context;
import org.dspace.core.ConfigurationManager;
import org.dspace.eperson.EPerson;

/**
 * CurationCli provides command-line access to Curation tools and processes.
 * 
 * @author richardrodgers
 */
public class CurationCli {

    @Option(name="-t", usage="curation task name")
    private String taskName;

    @Option(name="-T", usage="file containing curation task names")
    private String taskFileName;

    @Option(name="-g", usage="file containing groovy scripted task")
    private String scriptFileName;

    @Option(name="-i", usage="Id (handle) of object to perform task on, or 'all' to perform on whole repository")
    private String idName;

    @Option(name="-q", usage="name of task queue to process")
    private String taskQueueName;

    @Option(name="-n", usage="name of object selector to use")
    private String selectorName;

    @Option(name="-e", usage="email address of curating eperson")
    private String ePersonName;

    @Option(name="-r", usage="reporter to manage results - use '-' to report to console. If absent, no reporting")
    private String reporterName;

    @Option(name="-l", usage="maximum number of objects allowed in context cache. If absent, no limit")
    private String limit;

    @Option(name="-s", usage="transaction scope to impose: use 'object', 'curation', or 'open'. If absent, 'open' applies")
    private String scope;

    @Option(name="-j", usage="journal filter to apply: use 'n' for no journaling, 'a' for any status, or any combination of 's', 'f', 'k' (skip), 'e'. If absent, 'n' applies")
    private String jrnFilter;

    @Option(name="-v", usage="report execution details to stdout")
    private boolean verbose;

    @Option(name="-h", usage="display helpful message")
    private boolean help;

    private CurationCli() {}

    public static void main(String[] args) throws Exception {
        CurationCli cli = new CurationCli();
        CmdLineParser parser = new CmdLineParser(cli);
        try {
            parser.parseArgument(args);
            String errmsg = cli.validate();
            if (errmsg != null) {
                throw new CmdLineException(parser, errmsg);
            }
            if (cli.help) {
                parser.printUsage(System.err);
            } else {
                cli.curate();
            }
            System.exit(0);
        } catch (CmdLineException clE) {
            System.err.println(clE.getMessage());
            parser.printUsage(System.err);
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
        System.exit(1);
    }
    
    private String validate() {
    
        if (idName == null && taskQueueName == null && selectorName == null) {
            return "Id or selector must be specified: a handle, 'all', name of selector, or a task queue (-h for help)";
        }

        if (taskName == null && taskFileName == null && scriptFileName == null && taskQueueName == null) {
            return "A curation task, script or queue must be specified (-h for help)";
        }
        
        if (limit != null && Integer.parseInt(limit) <= 0 ) {
            return "Cache limit '" + limit + "' must be a positive integer";
        }
        
        if (scope != null && Curator.TxScope.valueOf(scope.toUpperCase()) == null) {
            return "Bad transaction scope '" + scope + "': only 'object', 'curation' or 'open' recognized";
        }
        return null;
    }
    
    private void curate() throws AuthorizeException, IOException, SQLException {
    
        Context c = new Context();
        if (ePersonName != null) {
            EPerson ePerson = EPerson.findByEmail(c, ePersonName);
            if (ePerson == null) {
                System.out.println("EPerson not found: " + ePersonName);
                System.exit(1);
            }
            c.setCurrentUser(ePerson);
        } else {
            c.turnOffAuthorisationSystem();
        }

        Curator curator = new Curator();
        CurationSession session = null;
        if (reporterName != null) {
            curator.setReporter(reporterName);
        }
        if (limit != null) {
            curator.setCacheLimit(Integer.parseInt(limit));
        }
        if (scope != null) {
            Curator.TxScope txScope = Curator.TxScope.valueOf(scope.toUpperCase());
            curator.setTransactionScope(txScope);
        }
        if (jrnFilter != null) {
            curator.setJournalFilter(jrnFilter);
        }
        // we are operating in batch mode, if anyone cares.
        curator.setInvoked(Curator.Invoked.BATCH);
        // load curation tasks
        if (taskName != null) {
            if (verbose) {
                System.out.println("Adding task: " + taskName);
            }
            curator.addTask(taskName);
            if (verbose && ! curator.hasTask(taskName)) {
                System.out.println("Task: " + taskName + " not resolved");
            }
        } else if (taskFileName != null) {
            // load taskFile
            try (BufferedReader reader = new BufferedReader(new FileReader(taskFileName))) {
                while ((taskName = reader.readLine()) != null) {
                    if (verbose) {
                        System.out.println("Adding task: " + taskName);
                    }
                    curator.addTask(taskName);
                }
            }
        } else if (scriptFileName != null) {
            session = Curator.newSession(new File(scriptFileName), "groovy");
        }
        // run tasks against object
        long start = System.currentTimeMillis();
        if (verbose) {
            System.out.println("Starting curation");
        }
        //if (scriptFileName != null) {
        //    CurationSession session = Curator.newSession();
        //    session.curate
        if (idName != null) {
            if (verbose) {
               System.out.println("Curating id: " + idName);
            }
            if ("all".equals(idName)) {
                // run on whole Site
                if (session == null) {
                    curator.curate(c, Site.getSiteHandle());
                } else {
                    session.curate(c, Site.getSiteHandle());
                }
            } else {
                if (session == null) {
                    curator.curate(c, idName);
                } else {
                    session.curate(c, idName);
                }
            }
        } else if (selectorName != null) {
            if (verbose) {
                System.out.println("Curating with selector: " + selectorName);
            }
            ObjectSelector selector = SelectorResolver.resolveSelector(c, selectorName);
            if (selector != null) {
                if (session == null) {
                    curator.curate(selector);
                } else {
                    session.curate(selector);
                }
            } else {
                System.out.println("No named selector found for: " + selectorName);
                throw new UnsupportedOperationException("No selector available");       		
            }
        } else {
            // process the task queue
            TaskQueue queue = (TaskQueue)ConfigurationManager.getInstance("curate", "taskqueue.impl");
            if (queue == null) {
                System.out.println("Error instantiating task queue");
                throw new UnsupportedOperationException("No queue service available");     
            }
            // use current time as our reader 'ticket'
            long ticket = System.currentTimeMillis();
            Iterator<TaskQueueEntry> entryIter = queue.dequeue(taskQueueName, ticket).iterator();
            while (entryIter.hasNext()) {
                TaskQueueEntry entry = entryIter.next();
                if (verbose) {
                    System.out.println("Curating id: " + entry.getObjectId());
                }
                curator.clear();
                // does entry relate to a DSO or workflow object?
                if (entry.getObjectId().indexOf("/") > 0) {
                    for (String task : entry.getTaskNames()) {
                        curator.addTask(task);
                    }
                    curator.curate(c, entry.getObjectId());
                } else {
                    // make eperson who queued task the effective user
                    EPerson agent = EPerson.findByEmail(c, entry.getEpersonId());
                    if (agent != null) {
                        c.setCurrentUser(agent);
                    }
                    WorkflowCurator.curate(curator, c, entry.getObjectId());
                }
            }
            queue.release(taskQueueName, ticket, true);
        }
        c.complete();
        if (session == null) {
            curator.complete();
        } else {
            session.close();
        }
        if (verbose) {
            long elapsed = System.currentTimeMillis() - start;
            System.out.println("Ending curation. Elapsed time: " + elapsed);
        }
    }
}
