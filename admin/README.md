# Admin Application Module #

This is a very rudimentary example of an mds _application module_, which is a maven project providing applications that run on an mds kernel. In this case, only one application has been implemented, namely the *ContentImport* command-line tool for batch loading content into an instance. This tool also offers a very simple means of putting real data into an instance for testing and evaluation.

## Content Import ##

Import communities, collections, and items into DSpace from directories in slightly reformed 'Simple Archive Format', or zip archives thereof. Based on ItemImport.
SAF 2.0 description: an archive directory contains a set of directories.
Directories must be named either 'destcoll' for destination collections, 'community[XX]' for communities, or 'item_nnn' for items
When sub-communities included, directory names *must* lexically sort parent to child:
    communityA parent (or community1, etc)
    communityB child
 
Each directory contains a file called 'metadata.xml'. Community and Collection directories may optionally contain a file named 'logo' which is the container logo bitstream. Item directories contain a mapping file called 'contents', and any other files referenced by this mapping file.
'metadata.xml' file has following structure:

    <?xml version="1.0" encoding="UTF-8" ?>
      <metadata>
        <mdvalues schema="dc">
      	  <mdvalue element="contributor" qualifier="author" language="us">Abelson, Hal</mdvalue>
          ....
        </mdvalues>
        <mdvalues schema="etd">
          ....
        </mdvalues>
      </metadata>
      
If the 'schema' attribute is not set, it is assumed to be an internal schema (e.g. as would be the case for community or collection metadata). 'element' is the only required attribute of the 'mdvalue' element.

The 'contents' file is a simple text file map of item bitstreams, one per line, with the following optional notations (tab-separated, order-insensitive, on same line):

    filename.pdf  bundle:<bundleName> permissions:<permissions> description:<description>\
                  primary:true source:<sourceInfo> metadata:<fileName>
                
where metadata files have the same format as item metadata, but pertain to the bitstreams. If bundle is not specified, configuration default used.

## Testing ##

*ContentImport* accepts URLs to zipped archives in addition to local directories. A sample import archive has been placed here:

<http://demo.dspace.org/jspui/bitstream/10673/51471/6/saf.zip>

So if you want to quickly fire up an mds instance with a little data (2 items in one collection), just use this URL.






