IF COL_LENGTH('dbo.services', 'version') IS NULL
BEGIN
    ALTER TABLE services
        ADD version BIGINT NOT NULL
            CONSTRAINT df_services_version DEFAULT 0;
END;
