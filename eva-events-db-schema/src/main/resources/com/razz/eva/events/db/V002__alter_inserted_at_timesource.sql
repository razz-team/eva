
ALTER TABLE uow_events ALTER COLUMN inserted_at SET DEFAULT clock_timestamp();

ALTER TABLE model_events ALTER COLUMN inserted_at SET DEFAULT clock_timestamp();
