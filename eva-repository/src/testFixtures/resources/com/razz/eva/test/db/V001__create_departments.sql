CREATE TYPE departments_state AS ENUM (
    'OWNED',
    'ORPHANED'
);

CREATE TABLE departments (
  id                UUID                NOT NULL PRIMARY KEY,

  name              TEXT                NOT NULL UNIQUE,
  boss              UUID                NOT NULL UNIQUE,
  headcount         INT                 NOT NULL,
  ration            TEXT                NOT NULL,

  state             DEPARTMENTS_STATE   NOT NULL,
  record_updated_at TIMESTAMP           NOT NULL,
  record_created_at TIMESTAMP           NOT NULL,
  version           BIGINT              NOT NULL
);
