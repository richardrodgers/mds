--
-- database_schema.sql
--
-- Version: $Revision: 6574 $
--
-- Date:    $Date: 2011-08-19 04:10:34 -0400 (Fri, 19 Aug 2011) $
--
-- Copyright (c) 2002-2009, The DSpace Foundation.  All rights reserved.
--
-- Redistribution and use in source and binary forms, with or without
-- modification, are permitted provided that the following conditions are
-- met:
--
-- - Redistributions of source code must retain the above copyright
-- notice, this list of conditions and the following disclaimer.
--
-- - Redistributions in binary form must reproduce the above copyright
-- notice, this list of conditions and the following disclaimer in the
-- documentation and/or other materials provided with the distribution.
--
-- - Neither the name of the DSpace Foundation nor the names of its
-- contributors may be used to endorse or promote products derived from
-- this software without specific prior written permission.
--
-- THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
-- ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
-- LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
-- A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
-- HOLDERS OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
-- INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
-- BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS
-- OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
-- ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
-- TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
-- USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH
-- DAMAGE.
--
--
--   DSpace SQL schema
--
--   Authors:   Peter Breton, Robert Tansley, David Stuve, Daniel Chudnov,
--              Richard Jones
--
--   This file is used as-is to initialize a database. Therefore,
--   table and view definitions must be ordered correctly.
--
--   Caution: THIS IS POSTGRESQL-SPECIFIC:
--
--   * SEQUENCES are used for automatic ID generation
--   * FUNCTION getnextid used for automatic ID generation
--
--
--   To convert to work with another database, you need to ensure
--   an SQL function 'getnextid', which takes a table name as an
--   argument, will return a safe new ID to use to create a new
--   row in that table.

-------------------------------------------------------
-- Function for obtaining new IDs.
--
--   * The argument is a table name
--   * It returns a new ID safe to use for that table
--
--   The function reads the next value from the sequence
--   'tablename_seq'
-------------------------------------------------------
CREATE FUNCTION getnextid(VARCHAR(40)) RETURNS INTEGER AS
    'SELECT CAST (nextval($1 || ''_seq'') AS INTEGER) AS RESULT;' LANGUAGE SQL;


-------------------------------------------------------
-- Sequences for creating new IDs (primary keys) for
-- tables.  Each table must have a corresponding
-- sequence called 'tablename_seq'.
-------------------------------------------------------
CREATE SEQUENCE dspaceobject_seq;
CREATE SEQUENCE attribute_seq;
CREATE SEQUENCE bitstreamformatregistry_seq;
CREATE SEQUENCE fileextension_seq;
CREATE SEQUENCE bitstream_seq;
CREATE SEQUENCE site_seq;
CREATE SEQUENCE eperson_seq;
CREATE SEQUENCE epersongroup_seq MINVALUE 0;
CREATE SEQUENCE item_seq;
CREATE SEQUENCE bundle_seq;
CREATE SEQUENCE item2bundle_seq;
CREATE SEQUENCE bundle2bitstream_seq;
CREATE SEQUENCE dcvalue_seq;
CREATE SEQUENCE community_seq;
CREATE SEQUENCE collection_seq;
CREATE SEQUENCE community2community_seq;
CREATE SEQUENCE community2collection_seq;
CREATE SEQUENCE collection2item_seq;
CREATE SEQUENCE resourcepolicy_seq;
CREATE SEQUENCE epersongroup2eperson_seq;
CREATE SEQUENCE handle_seq;
CREATE SEQUENCE workspaceitem_seq;
CREATE SEQUENCE workflowitem_seq;
CREATE SEQUENCE tasklistitem_seq;
CREATE SEQUENCE registrationdata_seq;
CREATE SEQUENCE subscription_seq;
CREATE SEQUENCE communities2item_seq;
CREATE SEQUENCE epersongroup2workspaceitem_seq;
CREATE SEQUENCE metadataschemaregistry_seq;
CREATE SEQUENCE metadatafieldregistry_seq;
CREATE SEQUENCE metadatavalue_seq;
CREATE SEQUENCE group2group_seq;
CREATE SEQUENCE group2groupcache_seq;
CREATE SEQUENCE harvested_collection_seq;
CREATE SEQUENCE harvested_item_seq;
CREATE SEQUENCE xresmap_seq;
CREATE SEQUENCE email_template_seq;
CREATE SEQUENCE mdtemplate_seq;
CREATE SEQUENCE mdtemplatevalue_seq;
CREATE SEQUENCE mdview_seq;
CREATE SEQUENCE mddisplay_seq;
CREATE SEQUENCE mdspec_seq;
CREATE SEQUENCE mdfldspec_seq;
CREATE SEQUENCE packingspec_seq;
CREATE SEQUENCE command_seq;
CREATE SEQUENCE cjournal_seq;
CREATE SEQUENCE ctask_seq;
CREATE SEQUENCE ctask_group_seq;
CREATE SEQUENCE group2ctask_seq;
CREATE SEQUENCE ctask_queue_seq;

-------------------------------------------------------
-- DSpaceObject table
-------------------------------------------------------
CREATE TABLE DSpaceObject
(
  dso_id               INTEGER PRIMARY KEY DEFAULT NEXTVAL('dspaceobject_seq'),
  dso_type_id          INTEGER,
  -- UUID as string
  object_id            VARCHAR(36) UNIQUE
);

-- indexing TODO

-------------------------------------------------------
-- DSpaceObject Scoped Attribute
-------------------------------------------------------
CREATE TABLE Attribute
(
  attribute_id       INTEGER PRIMARY KEY DEFAULT NEXTVAL('attribute_seq'),
  dso_id             INTEGER REFERENCES DSpaceObject(dso_id),
  scope              VARCHAR(128),
  attr_name          VARCHAR,
  attr_value         VARCHAR
);

-- Indexing TODO

-------------------------------------------------------
-- BitstreamFormatRegistry table
-------------------------------------------------------
CREATE TABLE BitstreamFormatRegistry
(
  bitstream_format_id INTEGER PRIMARY KEY,
  mimetype            VARCHAR(256),
  short_description   VARCHAR(128) UNIQUE,
  description         TEXT,
  support_level       INTEGER,
  -- Identifies internal types
  internal             BOOL
);

-------------------------------------------------------
-- FileExtension table
-------------------------------------------------------
CREATE TABLE FileExtension
(
  file_extension_id    INTEGER PRIMARY KEY,
  bitstream_format_id  INTEGER REFERENCES BitstreamFormatRegistry(bitstream_format_id),
  extension            VARCHAR(16)
);

CREATE INDEX fe_bitstream_fk_idx ON FileExtension(bitstream_format_id);

-------------------------------------------------------
-- Bitstream table
-------------------------------------------------------
CREATE TABLE Bitstream
(
   bitstream_id            INTEGER PRIMARY KEY,
   dso_id                  INTEGER REFERENCES DSpaceObject(dso_id),
   bitstream_format_id     INTEGER REFERENCES BitstreamFormatRegistry(bitstream_format_id),
   name                    VARCHAR(256),
   size_bytes              BIGINT,
   checksum                VARCHAR(64),
   checksum_algorithm      VARCHAR(32),
   source                  VARCHAR(256),
   internal_id             VARCHAR(256),
   deleted                 BOOL,
   store_number            INTEGER,
   sequence_id             INTEGER
);

CREATE INDEX bitstream_dso_fk_idx ON Bitstream(dso_id);
CREATE INDEX bit_bitstream_fk_idx ON Bitstream(bitstream_format_id);

---------------------------------
-- Site table (singleton)        
---------------------------------
CREATE TABLE Site
(
  site_id            INTEGER PRIMARY KEY,
  dso_id             INTEGER REFERENCES DSpaceObject(dso_id),
  name               VARCHAR(128),
  logo_bitstream_id  INTEGER REFERENCES Bitstream(bitstream_id)
);   

-------------------------------------------------------
-- EPerson table
-------------------------------------------------------
CREATE TABLE EPerson
(
  eperson_id          INTEGER PRIMARY KEY,
  dso_id              INTEGER REFERENCES DSpaceObject(dso_id),
  email               VARCHAR(64) UNIQUE,
  password            VARCHAR(64),
  firstname           VARCHAR(64),
  lastname            VARCHAR(64),
  can_log_in          BOOL,
  require_certificate BOOL,
  self_registered     BOOL,
  last_active         TIMESTAMP,
  sub_frequency       INTEGER,
  phone               VARCHAR(32),
  netid               VARCHAR(64),
  language            VARCHAR(64)
);

CREATE INDEX eperson_dso_fk_idx ON EPerson(dso_id);
-- index by email
CREATE INDEX eperson_email_idx ON EPerson(email);

-- index by netid
CREATE INDEX eperson_netid_idx ON EPerson(netid);

-------------------------------------------------------
-- EPersonGroup table
-------------------------------------------------------
CREATE TABLE EPersonGroup
(
  eperson_group_id INTEGER PRIMARY KEY,
  dso_id           INTEGER REFERENCES DSpaceObject(dso_id),
  name             VARCHAR(256) UNIQUE
);

CREATE INDEX group_dso_fk_idx ON EPersonGroup(dso_id);
------------------------------------------------------
-- Group2Group table, records group membership in other groups
------------------------------------------------------
CREATE TABLE Group2Group
(
  id        INTEGER PRIMARY KEY,
  parent_id INTEGER REFERENCES EPersonGroup(eperson_group_id),
  child_id  INTEGER REFERENCES EPersonGroup(eperson_group_id)
);

CREATE INDEX g2g_parent_fk_idx ON Group2Group(parent_id);
CREATE INDEX g2g_child_fk_idx ON Group2Group(child_id);

------------------------------------------------------
-- Group2GroupCache table, is the 'unwound' hierarchy in
-- Group2Group.  It explicitly names every parent child
-- relationship, even with nested groups.  For example,
-- If Group2Group lists B is a child of A and C is a child of B,
-- this table will have entries for parent(A,B), and parent(B,C)
-- AND parent(A,C) so that all of the child groups of A can be
-- looked up in a single simple query
------------------------------------------------------
CREATE TABLE Group2GroupCache
(
  id        INTEGER PRIMARY KEY,
  parent_id INTEGER REFERENCES EPersonGroup(eperson_group_id),
  child_id  INTEGER REFERENCES EPersonGroup(eperson_group_id)
);

CREATE INDEX g2gc_parent_fk_idx ON Group2Group(parent_id);
CREATE INDEX g2gc_child_fk_idx ON Group2Group(child_id);

-------------------------------------------------------
-- Item table
-------------------------------------------------------
CREATE TABLE Item
(
  item_id         INTEGER PRIMARY KEY,
  dso_id          INTEGER REFERENCES DSpaceObject(dso_id),
  submitter_id    INTEGER REFERENCES EPerson(eperson_id),
  in_archive      BOOL,
  withdrawn       BOOL,
  last_modified   TIMESTAMP WITH TIME ZONE,
  owning_collection INTEGER
);

CREATE INDEX item_dso_fk_idx ON Item(dso_id);
CREATE INDEX item_submitter_fk_idx ON Item(submitter_id);

-------------------------------------------------------
-- Bundle table
-------------------------------------------------------
CREATE TABLE Bundle
(
  bundle_id          INTEGER PRIMARY KEY,
  dso_id             INTEGER REFERENCES DSpaceObject(dso_id),
  name               VARCHAR(16),  -- ORIGINAL | THUMBNAIL | TEXT
  primary_bitstream_id  INTEGER REFERENCES Bitstream(bitstream_id)
);

CREATE INDEX bundle_dso_fk_idx ON Bundle(dso_id);
CREATE INDEX bundle_primary_fk_idx ON Bundle(primary_bitstream_id);

-------------------------------------------------------
-- Item2Bundle table
-------------------------------------------------------
CREATE TABLE Item2Bundle
(
  id        INTEGER PRIMARY KEY,
  item_id   INTEGER REFERENCES Item(item_id),
  bundle_id INTEGER REFERENCES Bundle(bundle_id)
);

-- index by item_id
CREATE INDEX item2bundle_item_idx on Item2Bundle(item_id);

CREATE INDEX item2bundle_bundle_fk_idx ON Item2Bundle(bundle_id);

-------------------------------------------------------
-- Bundle2Bitstream table
-------------------------------------------------------
CREATE TABLE Bundle2Bitstream
(
  id              INTEGER PRIMARY KEY,
  bundle_id       INTEGER REFERENCES Bundle(bundle_id),
  bitstream_id    INTEGER REFERENCES Bitstream(bitstream_id),
  bitstream_order INTEGER
);

-- index by bundle_id
CREATE INDEX bundle2bitstream_bundle_idx ON Bundle2Bitstream(bundle_id);

CREATE INDEX bundle2bitstream_bitstream_fk_idx ON Bundle2Bitstream(bitstream_id);

-------------------------------------------------------
-- Metadata Tables and Sequences
-------------------------------------------------------
CREATE TABLE MetadataSchemaRegistry
(
  metadata_schema_id INTEGER PRIMARY KEY DEFAULT NEXTVAL('metadataschemaregistry_seq'),
  namespace          VARCHAR(256) UNIQUE,
  short_id           VARCHAR(32) UNIQUE,
  sealed             BOOL
);

CREATE TABLE MetadataFieldRegistry
(
  metadata_field_id   INTEGER PRIMARY KEY DEFAULT NEXTVAL('metadatafieldregistry_seq'),
  metadata_schema_id  INTEGER NOT NULL REFERENCES MetadataSchemaRegistry(metadata_schema_id),
  element             VARCHAR(64),
  qualifier           VARCHAR(64),
  scope_note          TEXT
);

CREATE TABLE MetadataValue
(
  metadata_value_id  INTEGER PRIMARY KEY DEFAULT NEXTVAL('metadatavalue_seq'),
  dso_id             INTEGER REFERENCES DSpaceObject(dso_id),
  metadata_field_id  INTEGER REFERENCES MetadataFieldRegistry(metadata_field_id),
  text_value         TEXT,
  text_lang          VARCHAR(24),
  place              INTEGER
);

-- Create a dcvalue view for backwards compatibilty
CREATE VIEW dcvalue AS
  SELECT MetadataValue.metadata_value_id AS "dc_value_id", MetadataValue.dso_id,
    MetadataValue.metadata_field_id AS "dc_type_id", MetadataValue.text_value,
    MetadataValue.text_lang, MetadataValue.place
  FROM MetadataValue, MetadataFieldRegistry
  WHERE MetadataValue.metadata_field_id = MetadataFieldRegistry.metadata_field_id
  AND MetadataFieldRegistry.metadata_schema_id = 1;

-- An index for dso_id - almost all access is based on
-- instantiating the dso object, which grabs all values
-- related to that dso
CREATE INDEX metadatavalue_dso_idx ON MetadataValue(dso_id);
CREATE INDEX metadatavalue_dso_idx2 ON MetadataValue(dso_id,metadata_field_id);
CREATE INDEX metadatavalue_field_fk_idx ON MetadataValue(metadata_field_id);
CREATE INDEX metadatafield_schema_idx ON MetadataFieldRegistry(metadata_schema_id);

-------------------------------------------------------
-- Community table
-------------------------------------------------------
CREATE TABLE Community
(
  community_id      INTEGER PRIMARY KEY,
  dso_id            INTEGER REFERENCES DSpaceObject(dso_id),
  name              VARCHAR(128),
  logo_bitstream_id INTEGER REFERENCES Bitstream(bitstream_id),
  admin             INTEGER REFERENCES EPersonGroup(eperson_group_id)
);

CREATE INDEX community_dso_fk_idx ON Community(dso_id);
CREATE INDEX community_logo_fk_idx ON Community(logo_bitstream_id);
CREATE INDEX community_admin_fk_idx ON Community(admin);

-------------------------------------------------------
-- Collection table
-------------------------------------------------------
CREATE TABLE Collection
(
  collection_id     INTEGER PRIMARY KEY,
  dso_id            INTEGER REFERENCES DSpaceObject(dso_id),
  name              VARCHAR(128),
  logo_bitstream_id INTEGER REFERENCES Bitstream(bitstream_id),
  workflow_step_1   INTEGER REFERENCES EPersonGroup( eperson_group_id ),
  workflow_step_2   INTEGER REFERENCES EPersonGroup( eperson_group_id ),
  workflow_step_3   INTEGER REFERENCES EPersonGroup( eperson_group_id ),
  submitter         INTEGER REFERENCES EPersonGroup( eperson_group_id ),
  admin             INTEGER REFERENCES EPersonGroup( eperson_group_id )
);

CREATE INDEX collection_dso_fk_idx ON Collection(dso_id);
CREATE INDEX collection_logo_fk_idx ON Collection(logo_bitstream_id);
CREATE INDEX collection_workflow1_fk_idx ON Collection(workflow_step_1);
CREATE INDEX collection_workflow2_fk_idx ON Collection(workflow_step_2);
CREATE INDEX collection_workflow3_fk_idx ON Collection(workflow_step_3);
CREATE INDEX collection_submitter_fk_idx ON Collection(submitter);
CREATE INDEX collection_admin_fk_idx ON Collection(admin);

-------------------------------------------------------
-- Community2Community table
-------------------------------------------------------
CREATE TABLE Community2Community
(
  id             INTEGER PRIMARY KEY,
  parent_comm_id INTEGER REFERENCES Community(community_id),
  child_comm_id  INTEGER,
  CONSTRAINT com2com_child_fk FOREIGN KEY (child_comm_id) REFERENCES Community(community_id) DEFERRABLE
);

CREATE INDEX com2com_parent_fk_idx ON Community2Community(parent_comm_id);
CREATE INDEX com2com_child_fk_idx ON Community2Community(child_comm_id);

-------------------------------------------------------
-- Community2Collection table
-------------------------------------------------------
CREATE TABLE Community2Collection
(
  id             INTEGER PRIMARY KEY,
  community_id   INTEGER REFERENCES Community(community_id),
  collection_id  INTEGER,
  CONSTRAINT comm2coll_collection_fk FOREIGN KEY (collection_id) REFERENCES Collection(collection_id) DEFERRABLE
);

-- Index on community ID
CREATE INDEX Community2Collection_community_id_idx ON Community2Collection(community_id);
-- Index on collection ID
CREATE INDEX Community2Collection_collection_id_idx ON Community2Collection(collection_id);

-------------------------------------------------------
-- Collection2Item table
-------------------------------------------------------
CREATE TABLE Collection2Item
(
  id            INTEGER PRIMARY KEY,
  collection_id INTEGER REFERENCES Collection(collection_id),
  item_id       INTEGER,
  CONSTRAINT coll2item_item_fk FOREIGN KEY (item_id) REFERENCES Item(item_id) DEFERRABLE
);

-- index by collection_id
CREATE INDEX collection2item_collection_idx ON Collection2Item(collection_id);
-- and item_id
CREATE INDEX Collection2Item_item_id_idx ON Collection2Item( item_id );

-------------------------------------------------------
-- ResourcePolicy table
-------------------------------------------------------
CREATE TABLE ResourcePolicy
(
  policy_id            INTEGER PRIMARY KEY,
  resource_type_id     INTEGER,
  resource_id          INTEGER,
  action_id            INTEGER,
  eperson_id           INTEGER REFERENCES EPerson(eperson_id),
  epersongroup_id      INTEGER REFERENCES EPersonGroup(eperson_group_id),
  start_date           DATE,
  end_date             DATE
);

-- index by resource_type,resource_id - all queries by
-- authorization manager are select type=x, id=y, action=z
CREATE INDEX resourcepolicy_type_id_idx ON ResourcePolicy(resource_type_id,resource_id);

CREATE INDEX rp_eperson_fk_idx ON ResourcePolicy(eperson_id);
CREATE INDEX rp_epersongroup_fk_idx ON ResourcePolicy(epersongroup_id);

-------------------------------------------------------
-- EPersonGroup2EPerson table
-------------------------------------------------------
CREATE TABLE EPersonGroup2EPerson
(
  id               INTEGER PRIMARY KEY,
  eperson_group_id INTEGER REFERENCES EPersonGroup(eperson_group_id),
  eperson_id       INTEGER REFERENCES EPerson(eperson_id)
);

-- Index by group ID (used heavily by AuthorizeManager)
CREATE INDEX epersongroup2eperson_group_idx on EPersonGroup2EPerson(eperson_group_id);

CREATE INDEX epg2ep_eperson_fk_idx ON EPersonGroup2EPerson(eperson_id);

-------------------------------------------------------
-- Handle table
-------------------------------------------------------
CREATE TABLE Handle
(
  handle_id        INTEGER PRIMARY KEY,
  handle           VARCHAR(256) UNIQUE,
  resource_type_id INTEGER,
  resource_id      INTEGER
);

-- index by handle, commonly looked up
CREATE INDEX handle_handle_idx ON Handle(handle);
-- index by resource id and resource type id
CREATE INDEX handle_resource_id_and_type_idx ON handle(resource_id, resource_type_id);

-------------------------------------------------------
--  WorkspaceItem table
-------------------------------------------------------
CREATE TABLE WorkspaceItem
(
  workspace_item_id INTEGER PRIMARY KEY,
  item_id           INTEGER REFERENCES Item(item_id),
  collection_id     INTEGER REFERENCES Collection(collection_id),
  -- Answers to questions on first page of submit UI
  multiple_titles   BOOL,
  published_before  BOOL,
  multiple_files    BOOL,
  -- How for the user has got in the submit process
  stage_reached     INTEGER,
  page_reached      INTEGER
);

CREATE INDEX workspace_item_fk_idx ON WorkspaceItem(item_id);
CREATE INDEX workspace_coll_fk_idx ON WorkspaceItem(collection_id);

-------------------------------------------------------
--  WorkflowItem table
-------------------------------------------------------
CREATE TABLE WorkflowItem
(
  workflow_id    INTEGER PRIMARY KEY,
  item_id        INTEGER REFERENCES Item(item_id) UNIQUE,
  collection_id  INTEGER REFERENCES Collection(collection_id),
  state          INTEGER,
  owner          INTEGER REFERENCES EPerson(eperson_id),

  -- Answers to questions on first page of submit UI
  multiple_titles       BOOL,
  published_before      BOOL,
  multiple_files        BOOL
  -- Note: stage reached not applicable here - people involved in workflow
  -- can always jump around submission UI

);

CREATE INDEX workflow_item_fk_idx ON WorkflowItem(item_id);
CREATE INDEX workflow_coll_fk_idx ON WorkflowItem(collection_id);
CREATE INDEX workflow_owner_fk_idx ON WorkflowItem(owner);

-------------------------------------------------------
--  TasklistItem table
-------------------------------------------------------
CREATE TABLE TasklistItem
(
  tasklist_id   INTEGER PRIMARY KEY,
  eperson_id    INTEGER REFERENCES EPerson(eperson_id),
  workflow_id   INTEGER REFERENCES WorkflowItem(workflow_id)
);

CREATE INDEX tasklist_eperson_fk_idx ON TasklistItem(eperson_id);
CREATE INDEX tasklist_workflow_fk_idx ON TasklistItem(workflow_id);

-------------------------------------------------------
--  RegistrationData table
-------------------------------------------------------
CREATE TABLE RegistrationData
(
  registrationdata_id   INTEGER PRIMARY KEY,
  email                 VARCHAR(64) UNIQUE,
  token                 VARCHAR(48),
  expires               TIMESTAMP
);

-------------------------------------------------------
--  Subscription table
-------------------------------------------------------
CREATE TABLE Subscription
(
  subscription_id   INTEGER PRIMARY KEY,
  eperson_id        INTEGER REFERENCES EPerson(eperson_id),
  collection_id     INTEGER REFERENCES Collection(collection_id)
);

CREATE INDEX subs_eperson_fk_idx ON Subscription(eperson_id);
CREATE INDEX subs_collection_fk_idx ON Subscription(collection_id);

-------------------------------------------------------------------------------
-- EPersonGroup2WorkspaceItem table
-------------------------------------------------------------------------------

CREATE TABLE epersongroup2workspaceitem
(
  id integer DEFAULT nextval('epersongroup2workspaceitem_seq'),
  eperson_group_id integer REFERENCES EPersonGroup(eperson_group_id),
  workspace_item_id integer REFERENCES WorkspaceItem(workspace_item_id),
  CONSTRAINT epersongroup2item_pkey PRIMARY KEY (id)
);

CREATE INDEX epg2wi_group_fk_idx ON epersongroup2workspaceitem(eperson_group_id);
CREATE INDEX epg2wi_workspace_fk_idx ON epersongroup2workspaceitem(workspace_item_id);

-------------------------------------------------------
--  Communities2Item table
-------------------------------------------------------
CREATE TABLE Communities2Item
(
   id                      INTEGER PRIMARY KEY,
   community_id            INTEGER REFERENCES Community(community_id),
   item_id                 INTEGER REFERENCES Item(item_id)
);

-- Index by item_id for update/re-index
CREATE INDEX Communities2Item_item_id_idx ON Communities2Item( item_id );

CREATE INDEX Comm2Item_community_fk_idx ON Communities2Item( community_id );

-------------------------------------------------------
-- Community2Item view
------------------------------------------------------
CREATE VIEW Community2Item as
SELECT Community2Collection.community_id, Collection2Item.item_id
FROM Community2Collection, Collection2Item
WHERE Collection2Item.collection_id   = Community2Collection.collection_id
;

-------------------------------------------------------
--  Create 'special' groups, for anonymous access
--  and administrators
-------------------------------------------------------
-- We don't use getnextid() for 'anonymous' since the sequences start at '1'
--INSERT INTO epersongroup VALUES(0, 'Anonymous');
--INSERT INTO epersongroup VALUES(nextval('epersongroup_seq'), 'Administrator');

-------------------------------------------------------
-- Create the harvest settings table
-------------------------------------------------------
-- Values used by the OAIHarvester to harvest a collection
-- HarvestInstance is the DAO class for this table

CREATE TABLE harvested_collection
(
    collection_id INTEGER REFERENCES collection(collection_id) ON DELETE CASCADE,
    harvest_type INTEGER,
    oai_source VARCHAR,
    oai_set_id VARCHAR,
    harvest_message VARCHAR,
    metadata_config_id VARCHAR,
    harvest_status INTEGER,
    harvest_start_time TIMESTAMP WITH TIME ZONE,
    last_harvested TIMESTAMP WITH TIME ZONE,
    id INTEGER PRIMARY KEY
);

CREATE INDEX harvested_collection_fk_idx ON harvested_collection(collection_id);


CREATE TABLE harvested_item
(
    item_id INTEGER REFERENCES item(item_id) ON DELETE CASCADE,
    last_harvested TIMESTAMP WITH TIME ZONE,
    oai_id VARCHAR,
    id INTEGER PRIMARY KEY
);

CREATE INDEX harvested_item_fk_idx ON harvested_item(item_id);

-------------------------------------------------------
-- curation task tables
-------------------------------------------------------

CREATE TABLE ctask_data
(
    ctask_id        INTEGER PRIMARY KEY DEFAULT NEXTVAL('ctask_seq'),
    name            VARCHAR UNIQUE,
    description     VARCHAR,
    type            VARCHAR,
    impl            VARCHAR,
    load_addr       VARCHAR,
    script          VARCHAR,
    config          VARCHAR,
    install_date    TIMESTAMP,
    version         VARCHAR,
    info_url        VARCHAR
);

CREATE TABLE ctask_group
(
    ctask_group_id  INTEGER PRIMARY KEY DEFAULT NEXTVAL('ctask_group_seq'),
    type            VARCHAR,
    group_name      VARCHAR,
    description     VARCHAR,
    ui_access       BOOLEAN,
    api_access      BOOLEAN
);

CREATE TABLE group2ctask
(
    id              INTEGER PRIMARY KEY DEFAULT NEXTVAL('group2ctask_seq'),
    group_id        INTEGER REFERENCES ctask_group(ctask_group_id),
    ctask_id        INTEGER REFERENCES ctask_data(ctask_id)
);

CREATE TABLE ctask_queue
(
    ctask_queue_id   INTEGER PRIMARY KEY DEFAULT NEXTVAL('ctask_queue_seq'),
    queue_name       VARCHAR,
    task_list        VARCHAR,
    eperson_id       VARCHAR,
    enqueue_time     TIMESTAMP,
    target           VARCHAR,
    jrn_filter       VARCHAR,
    ticket           INTEGER
);

CREATE TABLE cjournal
(
  cjournal_id    INTEGER PRIMARY KEY DEFAULT NEXTVAL('cjournal_seq'),
  curation_date  TIMESTAMP,
  user_id        VARCHAR,
  task           VARCHAR,
  object_id      VARCHAR,
  status         INTEGER,
  result         VARCHAR
);

-------------------------------------------------------
-- xresmap table
-------------------------------------------------------
CREATE TABLE xresmap
(
    xresmap_id    INTEGER PRIMARY KEY DEFAULT NEXTVAL('xresmap_seq'),
    res_class     VARCHAR,
    mapping       VARCHAR,
    res_key       VARCHAR,
    resource      VARCHAR
);

-------------------------------------------------------
-- email template table
-------------------------------------------------------
CREATE TABLE email_template
(
    email_template_id    INTEGER PRIMARY KEY DEFAULT NEXTVAL('email_template_seq'),
    name                 VARCHAR UNIQUE,
    template             VARCHAR
);

------------------------------------------------------
-- mdtemplate table
------------------------------------------------------
CREATE TABLE mdtemplate
(
  mdtemplate_id      INTEGER PRIMARY KEY DEFAULT NEXTVAL('mdtemplate_seq'),
  description        TEXT
);

------------------------------------------------------
-- mdtemplatevalue table
------------------------------------------------------
CREATE TABLE mdtemplatevalue
(
  mdtemplate_value_id  INTEGER PRIMARY KEY DEFAULT NEXTVAL('mdtemplatevalue_seq'),
  mdtemplate_id        INTEGER REFERENCES mdtemplate(mdtemplate_id),
  metadata_field_id    INTEGER REFERENCES MetadataFieldRegistry(metadata_field_id),
  text_value           TEXT,
  text_lang            VARCHAR(24)
);

------------------------------------------------------
-- mdview table
------------------------------------------------------
CREATE TABLE mdview
(
  mdview_id          INTEGER PRIMARY KEY DEFAULT NEXTVAL('mdview_seq'),
  description        TEXT
);

------------------------------------------------------
-- mddisplay table
------------------------------------------------------
CREATE TABLE mddisplay
(
  mddisplay_id         INTEGER PRIMARY KEY DEFAULT NEXTVAL('mddisplay_seq'),
  mdview_id            INTEGER REFERENCES mdview(mdview_id),
  metadata_field_id    INTEGER REFERENCES MetadataFieldRegistry(metadata_field_id),
  altname              TEXT,
  label                TEXT,
  render_type          TEXT,
  wrapper              TEXT,
  disp_lang            VARCHAR(24),
  place                INTEGER
);

------------------------------------------------------
-- mdspec table
------------------------------------------------------
CREATE TABLE mdspec
(
  mdspec_id          INTEGER PRIMARY KEY DEFAULT NEXTVAL('mdspec_seq'),
  description        TEXT
);

------------------------------------------------------
-- mdfldspec table
------------------------------------------------------
CREATE TABLE mdfldspec
(
  mdfldspec_id         INTEGER PRIMARY KEY DEFAULT NEXTVAL('mdfldspec_seq'),
  mdspec_id            INTEGER REFERENCES mdspec(mdspec_id),
  metadata_field_id    INTEGER REFERENCES MetadataFieldRegistry(metadata_field_id),
  altname              TEXT,
  label                TEXT,
  description          TEXT,
  cardinality          TEXT,
  input_type           TEXT,
  locked               BOOL,
  disp_lang            VARCHAR(24),
  place                INTEGER
);


-------------------------------------------------------
-- packingspec table
-------------------------------------------------------
CREATE TABLE packingspec
(
  packingspec_id      INTEGER PRIMARY KEY DEFAULT NEXTVAL('packingspec_seq'),
  name                TEXT,
  description         TEXT,
  packer              TEXT,
  format              TEXT,
  content_filter      TEXT,
  metadata_filter     TEXT,
  reference_filter    TEXT,
  mimetype            TEXT,
  package_id          TEXT
);

-------------------------------------------------------
-- Command table
-------------------------------------------------------
CREATE TABLE command
(
  command_id           INTEGER PRIMARY KEY DEFAULT NEXTVAL('command_seq'),
  name                 VARCHAR UNIQUE,
  description          VARCHAR,
  class_name           VARCHAR,
  arguments            VARCHAR,
  launchable           BOOL,
  fwd_user_args        BOOL,
  successor            INTEGER 
);
