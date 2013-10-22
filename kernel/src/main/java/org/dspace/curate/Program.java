/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.curate;

import java.io.File;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.dspace.authorize.AuthorizeException;
import org.dspace.core.ConfigurationManager;
import org.dspace.content.DSpaceObject;
import org.dspace.curate.Curator;

import static org.dspace.curate.Curator.*;

/**
 * Program encapulates the behavior of curation programs, which
 * are instructions to orchestrate the invocation of one or more
 * curation tasks. The primary ability a program has is to
 * conditionalize invocations or other activity on task status codes:
 * i.e. establish rules that rely on the runtime outcomes of tasks.
 * A program is a simple text file, with syntax defined in documentation.
 * A brief example:
 *<code>
 *  # A Very Simple Program
 *  @Suspendable
 *  first-task
 *  if %ERROR %FAIL
 *    cleanup-task
 *  elif %SKIP
 *    report: just skipped object
 *  else
 *    second-task
 *  end
 *</code>
 * 
 * Programs are managed in a directory configured with the
 * dspace/config/modules/curate.cfg property "program.dir". 
 * To the curation runtime system, a program looks exactly
 * like an atomic task, and is invoked as such.
 *
 * @author richardrodgers
 */

public class Program extends AbstractCurationTask {
	// logging service
	private static Logger log = LoggerFactory.getLogger(Program.class);
	// base directory of program files
	private static final String programDir = ConfigurationManager.getProperty("curate", "program.dir");
  // program 'language' keywords
	private static final List<String> keywords = Arrays.asList("if", "elif", "else", "end");

	private enum Code {
		SUCCESS (CURATE_SUCCESS, "%SUCCESS"),
		FAIL    (CURATE_FAIL, "%FAIL"),
    SKIP    (CURATE_SKIP, "%SKIP"),
    ERROR   (CURATE_ERROR, "%ERROR");
    public final int code;
    public final String tag;
    Code(int code, String tag) {
        this.code = code;
        this.tag = tag;
    }

    public static int toCode(String tag) {
   	    for (Code c : Code.values()) {
    		    if (c.tag.equals(tag)) {
                return c.code;
    		    }
    	  }
    	  return CURATE_UNSET;
    }
	}

  // program status code
	private int progStatus = CURATE_UNSET;
	// root of program execution tree
	private Node startNode = null;
  // task resolver for compilation use
  private TaskResolver resolver = new TaskResolver();
  // program curator
  private Curator progCurator = null;
	
	public Program() {}

	/**
	 * Inititalizes the program - which means compiling the program source.
	 */
	@Override
	public void init(Curator curator, String taskId) throws IOException {
      super.init(curator, taskId);
      progCurator = new Curator();
      startNode = new Node(null);
      compile(taskId);
	}

	/**
	 * Compiles a program from a source file.
	 *
	 * @param progName the name of the curation program
	 */
	private void compile(String progName) {
      // Can we locate the file in the program directory?
      File progFile = new File(programDir, progName);
      if (progFile.exists()) {
          try (BufferedReader reader = new BufferedReader(new FileReader(progFile))) {
              String line = null;
              Node curNode = startNode;
              while((line = reader.readLine()) != null) {
                  line = line.trim();
                  // skip comment lines
					        if (! (line.startsWith("#") || line.startsWith("@")) ) {
					        	  // is it an action or a control statement?
                      if (isControl(line)) {
                          curNode = curNode.addControl(line);
                      } else if (isAction(line)) {
                          curNode = curNode.setAction(line);
                      } else {
                        // unknown - fail compilation
                        log.error("Illegal program statement: " + line);
                        throw new IOException("Illegal program statement");
                      }
						      }
				      }
			    } catch(IOException ioE) {
              log.error("Error reading program: " + progName);
          }
			} else {
          log.error("Program: " + progName + "not found in: " + programDir);
		  }
	}

  private boolean isControl(String line) {
      String[] parts = line.split(" ");
      return keywords.contains(parts[0]);
  }

  private boolean isAction(String line) {
      if (line.startsWith("%") && Code.toCode(line) != CURATE_UNSET) return true;
      if (line.startsWith("report")) return true;
      if (resolver.canResolveTask(line)) return true;
      return false;
  }
		
  /**
   * Performs the curation which runs the compiled program
   *
   * @param dso the DSpaceObject
   * @return status code
   */
  @Override
  public int perform(DSpaceObject dso) throws AuthorizeException, IOException, SQLException {
      return run(startNode, dso);
  }

  private int run(Node node, DSpaceObject dso) throws AuthorizeException, IOException, SQLException {
      int status = node.perform(dso);
      if (status != CURATE_UNSET) {
          progStatus = status;
      }
      Node next = node.next(status);
      return (next != null) ? run(next, dso) : CURATE_UNSET;
  }

  /*
   * Class defining a node in the program execution tree (graph).
   * Each node contains an 'action', whose value will be one of:
   * (1) a logical task name, optionally surrounded by '(' ')'
   * (2) the word 'report' followed by a string literal
   * (3) the token '%status' where status = 'SUCCESS', 'FAIL', 'ERROR' or 'SKIP'
   * Each node also contains a (possibly empty) map of successor nodes.
   * A node is evaluated by performing the action, using its status code as
   * a key into the successor map, and recursing.
   */
  private class Node {
      private String action;
      private ResolvedTask task;
      private Node parent;
      private Map<Integer, Node> branchMap;

      public Node(Node parent) {
          this.parent = parent;
          branchMap = new HashMap<>();
      }

      public Node setAction(String action) throws IOException {
        if (action == null) {
            this.action = action;
            if (! (action.startsWith("report") || action.startsWith("%"))) {
                task = resolver.resolveTask(action);
                task.init(progCurator);
            }
            return this;
        } else {
            Node next = new Node(this);
            mapAll(this, next);
            return next.setAction(action);
        }
      }

      public Node addControl(String statement) {
          String[] tokens = statement.split(" ");
          Node next = new Node(this);
          Node retNode = next;
          switch (tokens[0]) {
              case "if": mapStatus(this, next, tokens); break;
              case "elif": mapStatus(this.parent, next, tokens); break;
              case "else": mapAny(this.parent, next); break;
              case "end": retNode = this.parent; break;
              default: break;
          }
          return retNode;
      }

      public int perform(DSpaceObject dso) throws AuthorizeException, IOException, SQLException {
          int status = CURATE_UNSET;
  		    if (action.startsWith("%")) {
  		        // is it a status code literal - return it
  			      return Code.toCode(action);
  			  } else if (action.startsWith("report")) {
              curator.report(action.split(":")[1]);
              return progStatus;
          //} else if (action.startsWith("(") && action.endsWith(")")) {
          } else if (task != null) {
              // it is a task - invoke but ignore status if wrapped
  			  	  //String taskName = action.substring(1, action.length() - 2);
  			  	  progCurator.addInitializedTask(task);
              progCurator.curate(dso);
              status = progCurator.getStatus(task.getName());
              progCurator.removeTask(task.getName());
  		    }
  		    return status;
  	  }

      public Node next(int key) {
          return branchMap.get(key);
      }

      private void mapStatus(Node source, Node target, String[] tokens) {
          String[] codes = Arrays.copyOfRange(tokens, 1, tokens.length);
          for (String code: codes) {
              source.branchMap.put(Code.toCode(code), target);
          }
      }

      private void mapAny(Node source, Node target) {
          Map<Integer, Node> map = source.branchMap;
          for (Code key: Code.values()) {
              if (! map.containsValue(key.code)) {
                  map.put(key.code, target);
              }
          }
      }

      private void mapAll(Node source, Node target) {
          Map<Integer, Node> map = source.branchMap;
          map.put(Code.SUCCESS.code, target);
          map.put(Code.FAIL.code, target);
          map.put(Code.SKIP.code, target);
          map.put(Code.ERROR.code, target);
      }
  }
}
