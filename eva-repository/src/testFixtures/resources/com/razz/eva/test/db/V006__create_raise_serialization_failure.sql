CREATE FUNCTION raise_serialization_failure() RETURNS boolean AS $$
BEGIN
    RAISE SQLSTATE '40001' USING MESSAGE = 'Simulated serialization failure';
END $$ LANGUAGE plpgsql;
