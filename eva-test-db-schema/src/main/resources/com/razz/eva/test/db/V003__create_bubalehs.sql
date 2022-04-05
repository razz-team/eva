CREATE TYPE bubalehs_state AS ENUM (
    'SERVED',
    'CONSUMED'
);

CREATE TABLE bubalehs (
  id                UUID                NOT NULL PRIMARY KEY,

  employee_id       UUID                NOT NULL,
  state             BUBALEHS_STATE      NOT NULL,
  taste             VARCHAR(30)         NOT NULL,
  produced_on       TIMESTAMP           NOT NULL,
  volume            VARCHAR(30)         NOT NULL,

  record_updated_at TIMESTAMP           NOT NULL,
  record_created_at TIMESTAMP           NOT NULL,
  version           BIGINT              NOT NULL
);

CREATE UNIQUE INDEX bubalehs_employee_id_non_consumed
    ON bubalehs(employee_id)
    WHERE state <> 'CONSUMED';