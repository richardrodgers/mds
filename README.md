# mds - modernized DSpace #

Repo contains DSpace code that has been refactored to achieve several goals:

* eliminate extraneous and obsolete dependencies
* update or replace libraries, frameworks, programming idioms, etc with current state of the art
* increase modularity by more rigorous separation of concerns in code packages

Please note that this experiment does not currently represent any kind of functional DSpace; rather, it provides a laboratory where ideas may be explored more easily than in the mainline code.
Also note that the experiment is in very early stages, so many decisions may be reconsidered or reverted. To make rapid refactoring easier, only a small (under 200 classes), but pivotal subset of DSpace has been used. Essentially, the _dspace-api_ project has been stripped down to a functional core here called the *kernel*, which comprises the content model, authZ/N, search, browse and curation.

## Changes thus far include: ##

### log4j -> slf4j/logback ###

log4j has been removed in favor of slf4j/logback. This should be uncontroversial, since sfl4j/logback has been 'standard practice' for new projects for quite some time, and is widely used in kindred work: for example, our Fedora brethren have embraced it.
Only migration-script assisted changes have been made, and a lot more could be done to leverage great features in logback to lower the cost of debug logging and the like.

### Apache commons-lang, collections, etc -> Google Guava ###

Again, only a superficial transformation has taken place, and there are substantial opportunities to look at DSpace code through the lens of a modern library like Guava. It was chosen for a variety of reasons, e.g. that a lot of the commons collection stuff did not use generics, etc

### SRB/jargon -> null ###

SRB storage support was added early in DSpace, but SRB has not been developed/supported for many years since a successor called 'iRODS' was released. We have utilized the 'Bitstore' storage interface that makes SRB a plugin, rather than a requirement/dependency.

### Apache commons-dbcp -> BoneCP ###

Initial replacement of dbcp, but much further work could be done to leverage advanced features of BoneCP (pool partitioning, etc).

### DCDate -> Joda time ###

DCDate (in which 2 expensive Calendar objects and 7 non-threadsafe DateFormat objects are created in the constructor) removed and at its former call sites are Joda time replacements.

### Apache commons-cli -> args4j ###

Commons CLI provides a standard way of composing and parsing command lines, but is somewhat verbose. For each option, one typically has to code:

    options.addOption("p", "parent", true, "parent community (handle or database ID)");
    // parse and create a command line
    // now assign to variable
    String parentId;
    if (line.hasOption('p')) {
        parentId = line.getOptionValue('p');
    }

That is, one must manually *inject* the option value into the desired state variable/member. Many newer libraries do this automatically:

    @Option(name="-p", usage="parent community (handle or database ID)")
    String parentId;
    
Not revolutionary, but more concise, more readable and thus likely less error-prone.

### config -> registry

A lot of application support data is held in configuration files of various sorts: _dspace.cfg_ and its modular property file brothers, XML files, etc. Other data DSpace considers more appropriate for the database, and good examples are _registry data_. These begin as XML files, but are loaded into the DB for quick access. The line between configuration and registry is somewhat porous, and hinges on issues like size, volatility, etc
We will experiment with re-drawing the line for several data types. A first example turns the script launcher data into a _command registry_ that is loaded into the DB. In addition to scaling a little better (not a big concern here), there is the additional advantage of more modular installation: whenever an add-on to DSpace includes a command-line tool, the registry can be easily updated automatically. No manual edit of launcher.xml is required.
Notice that kernel commands (included in kernel code) should be loaded at system install time, just like other registry files.

### DCValue -> MDValue ###

DCValue has been replaced with MDValue, which (along with a more descriptive name) is an immutable object class for holding metadata values. The APIs using DCValue have also been replaced with more modern java idioms, and all the deprecated methods (like _addDC()_) removed.

### PluginManager Unplugged ###

PluginManager has been removed in favor of both simpler (on the low end) and more powerful (on the high end, e.g. DI) alternatives. See discussion in mds wiki.

### Generic Metadata -> DSpaceObjects ###

All DSpaceObjects (Communities, Collections, Items, Bundles, Bitstreams, EPersons, Groups) can now possess _generic_ metadata sets (meaning any flat, name-spaced/schema-defined, qualified, multi-valued, and multi-language sets, the same as for Items) using the content API.

### Content Array APIs -> Generic List APIs ###

All content APIs that return or receive arrays of objects have been replaced with more modern idiomatic generic lists. For example the Item method:

    public Bundle[] getBundles() throws SQLException

    // new signature

    public List<Bundle> getBundles() throws IOException

This simplifies the method implementations, and results in easier-to-use code. 

### UUIDs -> DSpaceObjects ###

All DSpaceObjects are assigned a global unique identifier (i.e. independent of relational DB IDs, CNRI handles, etc). This internal or object identifier has many functions and has been proposed/prototyped in many development efforts (e.g. as part of the DSpace 2.0 work).

### Scoped Object Attributes  ###

A new DSpaceObject API has been added: _scoped attributes_. These are persistent (DB-resident), name-spaced (relative to a scope) sets of name/value pairs that can be assigned to any DSO. These attributes are _not_ considered part of the formal data model (like metadata), and are therefore not stored in an AIP, nor even expected to persist beyond particular contexts of use. They can, however, be used by workflow, submission, curation tasks, etc to securely and persistently associate simple data with DSOs. They are automatically purged on object deletion,
but may also be removed via the API.

### Pluggable Search ###

The search code has been refactored to allow different, pluggable _providers_ such as in-process Lucene, SOLR, etc. The code has also been modified to allow indexing of any DSpaceObject metadata: e.g. BitStream generic metadata. Finally, the refactor allows _multiple_ simultaneous indexes, so that one could, e.g. have an administrative index (e.g. Lucene), along with a pubic index (e.g. SOLR).

## Future Work? ##

Nothing definite, but there are many opportunities. Examples: replace stackable auth with proper JAAS abstraction, refactor authority into an extensible service.

_Suggestions and contributions welcome!_
