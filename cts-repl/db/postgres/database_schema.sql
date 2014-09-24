--
-- database_schema.sql
--
--   Replication Module SQL schema
--
--   This file is used as-is to initialize database tables. Therefore,
--   table and view definitions must be ordered correctly.
--
--   Caution: THIS IS POSTGRESQL-SPECIFIC:
--
--   * SEQUENCES are used for automatic ID generation
--
--

-------------------------------------------------------
-- Sequences for creating new IDs (primary keys) for
-- tables.  Each table must have a corresponding
-- sequence called 'tablename_seq'.
-------------------------------------------------------

CREATE SEQUENCE odometer_seq;

-------------------------------------------------------
-- Odometer table
-------------------------------------------------------
CREATE TABLE odometer
(
  odo_id               INTEGER PRIMARY KEY DEFAULT NEXTVAL('odometer_seq'),
  count                BIGINT,
  storesize            BIGINT,
  uploaded             BIGINT,
  downloaded           BIGINT,
  modified             TIMESTAMP
);

-- indexing TODO

---------------------------
--- Initialize table
---------------------------
INSERT INTO odometer (count, storesize, uploaded, downloaded) VALUES (0,0,0,0);

