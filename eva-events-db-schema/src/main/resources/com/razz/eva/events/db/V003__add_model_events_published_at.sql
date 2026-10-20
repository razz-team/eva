-- Used by eva-polling-outbox.
-- Rows that exist before this migration are treated as already published:
-- a constant default is stored as the column's "missing value" (no table rewrite),
-- and dropping it right away makes every subsequent insert NULL, i.e. unpublished.
ALTER TABLE model_events ADD COLUMN published_at TIMESTAMP DEFAULT 'epoch';
ALTER TABLE model_events ALTER COLUMN published_at DROP DEFAULT;

-- Only unpublished rows are indexed, so the index stays small while the outbox keeps up.
-- Created on the partitioned parent, it is propagated to existing and future partitions.
-- NB: this takes a SHARE lock on model_events for the duration of the build;
-- for large tables build it per partition CONCURRENTLY and ATTACH instead.
CREATE INDEX model_events_unpublished_idx
    ON model_events (inserted_at, incremental_query_id)
    WHERE published_at IS NULL;
