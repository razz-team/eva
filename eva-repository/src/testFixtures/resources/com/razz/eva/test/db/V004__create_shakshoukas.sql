CREATE TYPE shakshoukas_state AS ENUM (
    'SERVED',
    'CONSUMED'
);

CREATE TABLE shakshoukas (
  id                UUID                NOT NULL PRIMARY KEY,

  employee_id       UUID                NOT NULL,
  state             SHAKSHOUKAS_STATE   NOT NULL,
  eggs_count        VARCHAR(30)         NOT NULL,
  with_pita         BOOLEAN             NOT NULL,

  record_updated_at TIMESTAMP           NOT NULL,
  record_created_at TIMESTAMP           NOT NULL,
  version           BIGINT              NOT NULL
);

CREATE UNIQUE INDEX shakshoukas_employee_id_non_consumed
    ON shakshoukas(employee_id)
    WHERE state <> 'CONSUMED';