# REST API for mds #

## API usage ##

The current REST API offers a service for CRUD operations on content objects, an authorization service, and a curation service to invoke tasks on objects.
Services accept and return either XML or JSON. API URLs appear under the context 'webapi'. 
Thus a URL to retrieve a bitstream might appear as:

    http://mdsrepo.mit.edu/webapi/content/1721.1/77095.5/media/my-file.pdf

### Content API ###

The content API (all URLs of which appear under the 'content' path) models DSpaceObjects as
resources that may contain sets of sub-resources. To obtain a representation of a resource (which here means a community,
collection, item or bitstream), one requests it via a persistent identifier (think handle):

    GET content/1721.1/2

The returned representation will contain one or more URIs for any sub-resources. For example, if the
above were an identifier for a collection, the representation would include the URI:

    content/1721.1/2/items

which when dereferenced would return a list of item URIs. The list below summarizes the sub-resources
available for each content type:

    community:  collections, subcommunities, mdsets, mdviews
    collection: items, mdsets, mdviews
    item:       filters, mdsets, mdviews
    bitstream:  mdsets, mdviews

A few explanations: first, _mdsets_ refer to metadata sets, i.e. collections of metadata that describe the
DSpace object. The sets currently defined correspond to the schemas configured in the metadata registry. Thus,
to request the Dublin Core metadata set for our resource, we would use the URI:

    content/1721.1/3/mdset/dc

Since mds has 'metadata for all' (i.e. all DSO types), one can request metadata on any object, not just items.
Closely related to metadata sets are _mdviews_, which are collections/projections of metadata values, profiled for particular purposes.
The service implementation may offer an arbitrary number of views, which may draw metadata from any number of sets. Metadata views,
unlike sets, are _read only_ resources. An example for an item might be:

    content/1721.1/3/mdview/public

Finally, _filters_ amount to a mechanism to expose the functionality of DSpace bundles, since they are used to
organize or group bitstreams in a item. An item resource will include a URI like:

     content/1721.1/3/filters

which will return a list of all the filters (bundles) defined on the item. One could then use the filter to restrict
a sub-resource list as follows:

    content/1721.1/3/filter/ORIGINAL

returning a list of bitstream URIs in the ORIGINAL bundle.

#### Content CRUD Operations ####

The previous section described the resource _access_ endpoints (obtained via HTTP GET methods). Modifying operations are defined below.
Creating new content resources is an operation typically performed on the _logical parent_ resource. For example, to create a collection that belongs to
the community 1721.1/1, one would POST a request to the resource URI:

    content/1721.1/1/collections

That is, the above URI is the resource name of the communities' _collection set_, not the community resource itself. Similarly,
adding a subcommunity is a POST to:

    content/1721.1/1/subcommunities

The only 'exception' to this rule is for top-level communities (which lack any parent resource). To add (create) a top-level community. POST to:

    content

Removing resources is quite simple: use the DELETE HTTP method on the resource URI:

    content/1721.1/1

would remove the community with handle '1721.1/1'. Updating metadata follows a slightly different pattern, since here the whole metadata set is the resource, not
individual values. Thus, one uses the recommended REST verb 'PUT' to the relevant metadata URI:

    content/1721.1/2/mdset/dc

In this case, the request body of the PUT would replace all the dc values in the object (other metadata would be unchanged).

#### Other Content Notes ####

* The URI 'content' (no other path components) is used as a discovery mechanism: it returns a list of the 'top-level' communities,
through which all other content can be found.

* Bitstream persistent IDs are simply the handle appended with the sequence number. So if 1721.1/4 is an item, 1721.1/4.1
is the first bitstream.

* To obtain the bitstream media streams, one uses the URL pattern:

    content/1721.1/4.1/media/mydoc.pdf

where 'mydoc.pdf' is the bitstream name. This is not strictly required, but the file suffix can act as a rendering hint to
certain clients (such as browsers).


### Curation API ###

The curation API is used to discover what tasks or other curation resources are installed on the server, and then invoke them
against content resources (or selectors). All API URLs appear under the 'curation' path.

To obtain a list of all task groups:

    GET curation/taskgroups

This resource lists URIs of all the groups. To obtain a list of tasks in a named group:

    curation/taskgroup/general

The same pattern is used for curation 'selectors':

     curation/selectorgroups
     curation/selectorgroup/general

To perform a curation on a content object, POST to the URI:

    curation/content/1721.1/1

or to curate a selection:

    curation/set/selector

The POST request entity body must contain the specifics of the curation 'order' e.g.:

    <curationOrder>
      <taskName>transmitAIP</taskName>
      <jrnFilter>a</jrnFilter>
      <txScope>object</txScope>
    </curationOrder>

### Authorization API ###

The authorization API exposes the primary objects used to assign and manage rights on content, viz. EPersons, Groups, and Resource Policies.
All API endpoints appear under the 'authz' path. To obtain a representation of an EPerson, Group or Policy one would request via GET:

    authz/eperson/123
    authz/group/234
    authz/policy/456

where '123' etc is the system id assigned to the object. For EPersons and Groups only, where there is a unique attribute (like email address for EPerson, or name for Group), it may be used instead of the system id. Thus the URI:

    authz/eperson/sthomas@mit.edu

would return a representation of the EPerson with the specified email if it existed.

The same URIs would be used (using ids only) with a DELETE method to delete the object, or a PUT method to update it.
The representation of a group resource  will include sub-resource URIs for its EPerson and Group members:

    authz/group/234/members
    authz/group/234/groupmembers

which when dereferenced will return entity reference lists of the EPeople and groups belonging to the group.
To create a new resource of any of these types, POST the request to the appropriate URI:

    authz/epeople
    authz/groups
    authz/policies

One special case arises for group membership lists. Since the lists can be extensive, the use of PUTs on a group resource to update it could be cumbersome, since to
adhere to the REST contract, the request body would have to include the entire membership list (and group membership list) in order to change in a single member.
Instead, the API for groups exposes what we can call _link resources_. For example:

    authz/group/234/eperson/123

This resource represents the membership of eperson 123 in group 234. Using a link resource, we can add a member to a group simply with a PUT request to the 
desired link resource URI (request body ignored). To remove a member from a group, DELETE the link resource. Note that neither operation has any direct effect on the underlying components: i.e. the PUT does not create a group or an eperson, and the DELETE does not remove the group or eperson. An additional use of link resources is for establishing whether an eperson or group is a member of a group. The response code alone from a GET request will determine the answer, without having to retrieve an entire membership list. Link resources can also describe group members:

    authz/group/234/group/789

Finally, the API supports limited 'discovery' of groups and EPersons. Simply append a query string to the base URI for the resource type:

    authz/epeople/mit.edu

would attempt to match all EPersons with 'mit.edu' in the email field, and return an entity reference list of EPersons found. Since resource policies are bound
to individual DSOs, there is no meaningful notion of discovery. To obtain the list of policies for an object, however, use the URI:

    authz/1721.1/2/policies

where the content identifiers ('1721.1/2') are the same as those exposed in the content API.










