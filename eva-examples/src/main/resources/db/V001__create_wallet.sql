CREATE TABLE wallet (
  id                        UUID           NOT NULL            ,
  currency                  TEXT           NOT NULL            ,
  amount                    BIGINT         NOT NULL            ,
  expire_at                 TIMESTAMP      NOT NULL            ,

  record_updated_at         TIMESTAMP      NOT NULL            ,
  record_created_at         TIMESTAMP      NOT NULL            ,
  version                   BIGINT         NOT NULL
);
