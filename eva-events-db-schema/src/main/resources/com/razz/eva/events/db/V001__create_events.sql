CREATE SEQUENCE uow_events_incremental_query_id_seq AS BIGINT;

CREATE TABLE events.uow_events (
  id                    UUID                NOT NULL,
  name                  TEXT                NOT NULL,
  idempotency_key       TEXT,
  principal_name        TEXT                NOT NULL,
  principal_id          TEXT                NOT NULL,
  occurred_at           TIMESTAMP           NOT NULL,
  inserted_at           TIMESTAMP           NOT NULL DEFAULT LOCALTIMESTAMP,
  model_events          UUID[]              NOT NULL,
  params                TEXT                NOT NULL DEFAULT '{}',
  incremental_query_id  BIGINT              NOT NULL DEFAULT nextval('uow_events_incremental_query_id_seq')
) PARTITION BY RANGE(inserted_at);

ALTER SEQUENCE uow_events_incremental_query_id_seq OWNED BY uow_events.incremental_query_id;

CREATE INDEX uow_events_inserted_at_idx ON uow_events (inserted_at);
ALTER TABLE uow_events ADD CONSTRAINT uow_events_principal_name_length
    CHECK (char_length(principal_name) > 0 AND char_length(principal_name) <= 100);
ALTER TABLE uow_events ADD CONSTRAINT uow_events_principal_id_length
    CHECK (char_length(principal_id) > 0 AND char_length(principal_id) <= 100);

CREATE OR REPLACE FUNCTION
    check_prev_partitions_for_idempotency_key() RETURNS TRIGGER AS
$body$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM events.uow_events
        WHERE
            name = NEW.name AND
            NEW.idempotency_key IS NOT NULL AND
            idempotency_key = NEW.idempotency_key AND
            inserted_at >= NEW.inserted_at - INTERVAL '10 days'
    ) THEN
        RAISE EXCEPTION 'uow_events_name_idempotency_key_idx violation [name: %] by [idempotency_key: %]',
        NEW.name,
        NEW.idempotency_key
        USING ERRCODE = 'unique_violation';
    ELSE
        RETURN NEW;
    END IF;
END;
$body$
LANGUAGE plpgsql;

CREATE TRIGGER check_idempotency_key_on_update
    BEFORE UPDATE ON uow_events
    FOR EACH ROW
    WHEN (NEW.idempotency_key IS NOT NULL)
    EXECUTE FUNCTION check_prev_partitions_for_idempotency_key();

CREATE TRIGGER check_idempotency_key_on_insert
    BEFORE INSERT ON uow_events
    FOR EACH ROW
    WHEN (NEW.idempotency_key IS NOT NULL)
    EXECUTE FUNCTION check_prev_partitions_for_idempotency_key();

-- Template
CREATE TABLE uow_events_template (LIKE uow_events);
CREATE UNIQUE INDEX uow_events_template_name_idempotency_key_idx ON uow_events_template (name, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- By date part
DO $body$
BEGIN
    IF EXISTS(SELECT 1 FROM information_schema.schemata WHERE schema_name = 'partman') THEN
        PERFORM partman.create_parent(
            p_parent_table := 'events.uow_events',
            p_control := 'inserted_at',
            p_type := 'native',
            p_interval := '5 days',
            p_template_table := 'events.uow_events_template',
            p_premake := 2
        );

        UPDATE partman.part_config
        SET infinite_time_partitions = TRUE
        WHERE parent_table = 'events.uow_events';
    END IF;
END;
$body$;

CREATE SEQUENCE model_events_incremental_query_id_seq AS BIGINT;

CREATE TABLE model_events (
  id                    UUID                NOT NULL,
  uow_id                UUID                NOT NULL,
  model_id              TEXT                NOT NULL,
  name                  TEXT                NOT NULL,
  model_name            TEXT                NOT NULL,
  occurred_at           TIMESTAMP           NOT NULL,
  inserted_at           TIMESTAMP           NOT NULL DEFAULT LOCALTIMESTAMP,
  payload               TEXT                NOT NULL,
  tracing_context       TEXT                NOT NULL DEFAULT '{}',
  incremental_query_id  BIGINT              NOT NULL DEFAULT nextval('model_events_incremental_query_id_seq')
) PARTITION BY RANGE(inserted_at);

ALTER SEQUENCE model_events_incremental_query_id_seq OWNED BY model_events.incremental_query_id;

CREATE INDEX model_events_inserted_at_idx on events.model_events (inserted_at);

-- By date part
DO $body$
BEGIN
    IF EXISTS(SELECT 1 FROM information_schema.schemata WHERE schema_name = 'partman') THEN
        PERFORM partman.create_parent(
            p_parent_table := 'events.model_events',
            p_control := 'inserted_at',
            p_type := 'native',
            p_interval := '5 days',
            p_premake := 2
        );

        UPDATE partman.part_config
        SET infinite_time_partitions = TRUE
        WHERE parent_table = 'events.model_events';
    END IF;
END;
$body$;
