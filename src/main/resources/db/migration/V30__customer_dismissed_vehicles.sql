IF COL_LENGTH('dbo.vehicles', 'customer_dismissed') IS NULL
BEGIN
    ALTER TABLE dbo.vehicles
        ADD customer_dismissed BIT NOT NULL
            CONSTRAINT df_vehicles_customer_dismissed DEFAULT 0;
END
GO
