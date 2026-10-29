-- Used by eva-polling-outbox.
-- db-scheduler table, see https://github.com/kagkarlsson/db-scheduler/blob/master/db-scheduler/src/test/resources/postgresql_tables.sql
CREATE TABLE scheduled_tasks (
  task_name             TEXT                        NOT NULL,
  task_instance         TEXT                        NOT NULL,
  task_data             BYTEA,
  execution_time        TIMESTAMP WITH TIME ZONE    NOT NULL,
  picked                BOOLEAN                     NOT NULL,
  picked_by             TEXT,
  last_success          TIMESTAMP WITH TIME ZONE,
  last_failure          TIMESTAMP WITH TIME ZONE,
  consecutive_failures  INT,
  last_heartbeat        TIMESTAMP WITH TIME ZONE,
  version               BIGINT                      NOT NULL,
  priority              SMALLINT,
  PRIMARY KEY (task_name, task_instance)
);

CREATE INDEX scheduled_tasks_execution_time_idx ON scheduled_tasks (execution_time);
CREATE INDEX scheduled_tasks_last_heartbeat_idx ON scheduled_tasks (last_heartbeat);
CREATE INDEX scheduled_tasks_priority_execution_time_idx
    ON scheduled_tasks (priority DESC, execution_time ASC);
