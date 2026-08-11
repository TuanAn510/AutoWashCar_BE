IF COL_LENGTH('dbo.loyalty_accounts', 'version') IS NULL
BEGIN
    ALTER TABLE dbo.loyalty_accounts
        ADD version BIGINT NOT NULL CONSTRAINT df_loyalty_accounts_version DEFAULT 0;
END
GO

IF COL_LENGTH('dbo.promotions', 'version') IS NULL
BEGIN
    ALTER TABLE dbo.promotions
        ADD version BIGINT NOT NULL CONSTRAINT df_promotions_version DEFAULT 0;
END
GO

IF COL_LENGTH('dbo.vehicles', 'ownership_start_at') IS NULL
BEGIN
    ALTER TABLE dbo.vehicles
        ADD ownership_start_at DATETIME2 NOT NULL CONSTRAINT df_vehicles_ownership_start_at DEFAULT SYSUTCDATETIME();
END
GO

IF COL_LENGTH('dbo.vehicles', 'ownership_end_at') IS NULL
BEGIN
    ALTER TABLE dbo.vehicles
        ADD ownership_end_at DATETIME2 NULL;
END
GO

IF EXISTS (
    SELECT 1
    FROM sys.key_constraints
    WHERE name = 'uk_vehicles_license_plate'
      AND parent_object_id = OBJECT_ID('dbo.vehicles')
)
BEGIN
    ALTER TABLE dbo.vehicles DROP CONSTRAINT uk_vehicles_license_plate;
END
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = 'ux_vehicles_active_license_plate'
      AND object_id = OBJECT_ID('dbo.vehicles')
)
BEGIN
    CREATE UNIQUE INDEX ux_vehicles_active_license_plate
        ON dbo.vehicles(license_plate)
        WHERE is_active = 1;
END
GO

IF OBJECT_ID('dbo.point_lots', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.point_lots (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        customer_id BIGINT NOT NULL,
        earn_transaction_id BIGINT NOT NULL,
        initial_points INT NOT NULL,
        remaining_points INT NOT NULL,
        earned_at DATETIME2 NOT NULL,
        expires_at DATETIME2 NOT NULL,
        created_at DATETIME2 NOT NULL,
        updated_at DATETIME2 NOT NULL,
        CONSTRAINT fk_point_lots_customer FOREIGN KEY (customer_id) REFERENCES dbo.users(id),
        CONSTRAINT fk_point_lots_earn_transaction FOREIGN KEY (earn_transaction_id) REFERENCES dbo.loyalty_transactions(id),
        CONSTRAINT uk_point_lots_earn_transaction UNIQUE (earn_transaction_id),
        CONSTRAINT chk_point_lots_points CHECK (
            initial_points > 0
            AND remaining_points >= 0
            AND remaining_points <= initial_points
        )
    );

    CREATE INDEX idx_point_lots_customer_expiry
        ON dbo.point_lots(customer_id, expires_at, id)
        WHERE remaining_points > 0;
END
GO

IF OBJECT_ID('dbo.loyalty_monthly_snapshots', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.loyalty_monthly_snapshots (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        customer_id BIGINT NOT NULL,
        period_start DATE NOT NULL,
        review_points INT NOT NULL,
        review_spending DECIMAL(14,2) NOT NULL,
        review_visits BIGINT NOT NULL,
        tier_before_id BIGINT NULL,
        tier_after_id BIGINT NULL,
        reviewed_at DATETIME2 NOT NULL,
        created_at DATETIME2 NOT NULL,
        updated_at DATETIME2 NOT NULL,
        CONSTRAINT fk_loyalty_snapshots_customer FOREIGN KEY (customer_id) REFERENCES dbo.users(id),
        CONSTRAINT fk_loyalty_snapshots_tier_before FOREIGN KEY (tier_before_id) REFERENCES dbo.membership_tiers(id),
        CONSTRAINT fk_loyalty_snapshots_tier_after FOREIGN KEY (tier_after_id) REFERENCES dbo.membership_tiers(id),
        CONSTRAINT uk_loyalty_snapshots_customer_period UNIQUE (customer_id, period_start),
        CONSTRAINT chk_loyalty_snapshots_values CHECK (
            review_points >= 0
            AND review_spending >= 0
            AND review_visits >= 0
        )
    );
END
GO

IF OBJECT_ID('dbo.audit_logs', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.audit_logs (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        actor_id BIGINT NULL,
        target_user_id BIGINT NULL,
        action VARCHAR(80) NOT NULL,
        before_value TEXT NULL,
        after_value TEXT NULL,
        created_at DATETIME2 NOT NULL,
        updated_at DATETIME2 NOT NULL,
        CONSTRAINT fk_audit_logs_actor FOREIGN KEY (actor_id) REFERENCES dbo.users(id),
        CONSTRAINT fk_audit_logs_target_user FOREIGN KEY (target_user_id) REFERENCES dbo.users(id)
    );

    CREATE INDEX idx_audit_logs_target_created
        ON dbo.audit_logs(target_user_id, created_at);
END
GO
