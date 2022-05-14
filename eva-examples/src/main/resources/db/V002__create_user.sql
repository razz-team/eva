CREATE TABLE "user" (
  id                        UUID           NOT NULL PRIMARY KEY,
  first_name                TEXT           NOT NULL            ,
  last_name                 TEXT           NOT NULL            ,
  address                   TEXT           NOT NULL            ,

  record_updated_at         TIMESTAMP      NOT NULL            ,
  record_created_at         TIMESTAMP      NOT NULL            ,
  version                   BIGINT         NOT NULL
);
