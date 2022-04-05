CREATE TABLE employees (
  id                UUID                NOT NULL PRIMARY KEY,

  first_name        TEXT                NOT NULL,
  last_name         TEXT                NOT NULL,
  department_id     UUID                NOT NULL REFERENCES departments (id),
  email             TEXT                NOT NULL,
  ration            TEXT                NOT NULL,
  
  record_updated_at TIMESTAMP           NOT NULL,
  record_created_at TIMESTAMP           NOT NULL,
  version           BIGINT              NOT NULL
);

CREATE UNIQUE INDEX employees_name_idx ON employees (first_name, last_name);
CREATE UNIQUE INDEX employees_email_idx ON employees (email);
ALTER TABLE employees
ADD CONSTRAINT employees_first_name_len_check
CHECK (length(first_name) <= 20);