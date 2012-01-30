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

### Java Dates -> Joda time ###

Almost nothing has been done beyond declaring the dependency and a few date comparisons rewritten. But an examination of a class like DCDate (in which 2 expensive Calendar objects and 7 non-threadsafe DateFormat objects are created in the constructor) suggest many strides could be made.

## Future Work? ##

Nothing definite, but there are many opportunities. Examples: replace stackable auth with proper JAAS abstraction, refactor authority into an extensible service.

_Suggestions and contributions welcome!_
