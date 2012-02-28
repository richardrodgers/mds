# REST API for Curation #

## API usage ##

URLs all relative to where app is installed. Service accepts and returns either XML or JSON.

To obtain list of installed tasks, task groups, etc:

 * GET /rest/tasks
 * GET /rest/taskgroups
 * GET /rest/selectors
 * GET /rest/selectorgroups

To perform curation on a DSO or selector:

 * POST /rest/dso/<dsoId>
 * POST /rest/set/<selectorName>

(where dsoId typically a handle)

The POST request entity body must contain the specifics of the curation 'order' e.g.:

    <curationOrder>
      <tasks>
          <task>transmitAIP</task>
      </tasks>
      <txScope>object</txScope>
    </curationOrder>

## How To Build ##

Do a regular maven install of enclosed source:

    $ mvn install

Run the test server:

    $ mvn exec:java -Dexec.mainClass=org.dspace.rest.curate.TestServer -Ddspace.configuration=<known location>

where _known location_ is the full path to wherever you installed kernel.cfg, e.g. /home/rest/test/conf/kernel.cfg

This will bring up a localhost server (port 9998) that you may point REST clients at. Note that this setup will only allow protocol testing, there is no real DSpace (DB, etc) running, so no actual curation done.







