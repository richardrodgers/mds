# REST API for mds #

## API usage ##

The current REST API is not complete, but offers a read-only content service, and a read/write curation service.
Services accept and return either XML or JSON. API URLs appear under the context 'webapi'. 
Thus a URL to retrieve a bitstream might appear as:

    http://mdsrepo.mit.edu/webapi/content/1721.1/77095.5/media/my-file.pdf

### Content API ###

The content API (all URLs of which appear under the 'content' path) models DSpaceObjects as
resources that may contain sets of sub-resources. To obtain a resource (which here means a community,
collection, item or bitstream), one requests it via a persistent identifier (think handle):

    GET content/1721.1/2

The returned representation will contain one or more URIs for any sub-resources. For example, if the
above were an identifier for a collection, the representation would include the URI:

    content/1721.1/2/items

which when dereferenced would return a list of item URIs. The list below summarizes the sub-resources
for each content type:

    community:  collections, subcommunities, mdsets
    collection: items, mdsets
    item:       filters, mdsets
    bitstream:  mdsets

A few explanations: first, _mdsets_ refer to metadata sets, i.e. collections of metadata that describe the
DSpace object. The only sets currently defined correspond to the schemas configured in the registry. Thus,
to request the Dublin Core metadata set for our resource, we would use the URI:

    content/1721.1/3/mdset/dc

Since mds has 'metadata for all' (i.e. all DSO types), one can request metadata on any object, not just items.

Second, _filters_ amount to a mechanism to expose the functionality of DSpace bundles, since they are used to
organize or group bitstreams in a item. The item resource will include a URI like:

     content/1721.1/3/filters

which will return a list of all the filters (bundles) defined on the item. One could then use this to restrict
a sub-resource list as follows:

    content/1721.1/3/filter/ORIGINAL

returning a list of bitstreams only in the ORIGINAL bundle.

A few other notes:

* The URI 'content' (no other path) is used as a discovery mechanism: it returns a list of the 'top-level' communities,
through which all other content can be found.

* Bitstream persistent IDs are simply the handle appended with the sequence number. So if 1721.1/4 is an item, 1721.1/4.1
is the first bitstream.

* To obtain actual bitstreams, one uses the URL pattern:

    content/1721.1/4.1/media/mydoc.pdf

where 'mydoc.pdf' is the bitstream name. This is not strictly required, but the file suffix can act as a rendering hint to
certain clients (such as browsers).


### Curation API ###

To obtain list of installed tasks, task groups, etc:

 * GET curation/tasks
 * GET curation/taskgroups
 * GET curation/selectors
 * GET curation/selectorgroups

To perform curation on a DSO or selector:

 * POST curation/content/<dsoId>
 * POST curation/set/<selectorName>

(where dsoId typically is a handle)

The POST request entity body must contain the specifics of the curation 'order' e.g.:

    <curationOrder>
      <tasks>
          <task>transmitAIP</task>
      </tasks>
      <txScope>object</txScope>
    </curationOrder>







