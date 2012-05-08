# OAI-PMH Web Application Module #

This is a simple mds _application module_, which is a maven project providing applications that run on an mds kernel. In this case, the OAI-PMH data provider web service for batch publishing repository metadata.

## OAI-PMH Web Service ##

The code implements an OAI\_PMH v2.0 compliant service for a 'repository' (data provider), as defined by the specification. It currently only supports the required _oai_dc_ metadata prefix.

It is configured using 3 files, included in the module:

### oaicat.properties ###

This code utilizes OCLC's OAICat software library, which is configured by means of this file, located in the _conf_ directory. One should normally not have to alter the contents of this file, unless to add a new metadata prefix and supporting code.

### oai-pmh.cfg ###

This is the standard modular configuration file for the application, found in _conf/modules_. Again, normally, no changes need to be made to the default configuration.

### oaidc.properties ###

This file, found in _conf/crosswalks_, controls which metadata fields are exposed through the service, and how they are mapped to the 15 unqualified DC elements.
The syntax of the file is:

    dc.contributor.* = creator

where the mds metadata (or pattern, using wildcard matching) field name appears on the left, and the DC element on the right. To suppress any field, simply do not
declare a property for it. Note that the right side DC elements _must_ belong to the set:

    "title", "creator", "subject", "description", "publisher", "contributor",
    "date", "type", "format", "identifier", "source", "language",
    "relation", "coverage", "rights"

It is valid configuration to map non-DC metadata to OAI-PMH DC elements, and have many metadata fields map to the same DC element.
