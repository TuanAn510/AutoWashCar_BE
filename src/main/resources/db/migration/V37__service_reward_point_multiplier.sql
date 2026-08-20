IF COL_LENGTH('dbo.services', 'reward_multiplier') IS NULL
BEGIN
    ALTER TABLE dbo.services ADD reward_multiplier DECIMAL(3, 1) NULL;
END;
GO

UPDATE dbo.services
SET reward_multiplier = 1.0
WHERE reward_multiplier IS NULL;
GO

IF EXISTS (
    SELECT 1
    FROM sys.columns
    WHERE object_id = OBJECT_ID('dbo.services')
      AND name = 'reward_multiplier'
      AND is_nullable = 1
)
BEGIN
    ALTER TABLE dbo.services ALTER COLUMN reward_multiplier DECIMAL(3, 1) NOT NULL;
END;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.default_constraints
    WHERE parent_object_id = OBJECT_ID('dbo.services')
      AND name = 'df_services_reward_multiplier'
)
BEGIN
    ALTER TABLE dbo.services
        ADD CONSTRAINT df_services_reward_multiplier DEFAULT 1.0 FOR reward_multiplier;
END;
GO

IF EXISTS (
    SELECT 1 FROM sys.check_constraints
    WHERE parent_object_id = OBJECT_ID('dbo.services')
      AND name = 'chk_services_reward_points_price_rate'
)
BEGIN
    ALTER TABLE dbo.services DROP CONSTRAINT chk_services_reward_points_price_rate;
END;
GO

UPDATE dbo.services
SET reward_points = CONVERT(INT, FLOOR(FLOOR(price / 10000) * reward_multiplier));
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.check_constraints
    WHERE parent_object_id = OBJECT_ID('dbo.services')
      AND name = 'chk_services_reward_multiplier'
)
BEGIN
    ALTER TABLE dbo.services WITH CHECK
        ADD CONSTRAINT chk_services_reward_multiplier
        CHECK (
            reward_multiplier BETWEEN 1.0 AND 5.0
            AND reward_multiplier * 2 = FLOOR(reward_multiplier * 2)
        );
END;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.check_constraints
    WHERE parent_object_id = OBJECT_ID('dbo.services')
      AND name = 'chk_services_reward_points_multiplier_rate'
)
BEGIN
    ALTER TABLE dbo.services WITH CHECK
        ADD CONSTRAINT chk_services_reward_points_multiplier_rate
        CHECK (reward_points = CONVERT(INT, FLOOR(FLOOR(price / 10000) * reward_multiplier)));
END;
GO

IF COL_LENGTH('dbo.booking_services', 'reward_multiplier') IS NULL
BEGIN
    ALTER TABLE dbo.booking_services ADD reward_multiplier DECIMAL(3, 1) NULL;
END;
GO

UPDATE booking_service
SET reward_multiplier =
    CASE
        WHEN base_points.value > 0
             AND booking_service.reward_points BETWEEN base_points.value AND base_points.value * 5
             AND (booking_service.reward_points * 2) % base_points.value = 0
            THEN CONVERT(DECIMAL(3, 1), booking_service.reward_points * 1.0 / base_points.value)
        ELSE 1.0
    END
FROM dbo.booking_services booking_service
CROSS APPLY (
    SELECT CONVERT(INT, FLOOR(booking_service.price / 10000)) AS value
) base_points
WHERE booking_service.reward_multiplier IS NULL;
GO

IF EXISTS (
    SELECT 1
    FROM sys.columns
    WHERE object_id = OBJECT_ID('dbo.booking_services')
      AND name = 'reward_multiplier'
      AND is_nullable = 1
)
BEGIN
    ALTER TABLE dbo.booking_services ALTER COLUMN reward_multiplier DECIMAL(3, 1) NOT NULL;
END;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.default_constraints
    WHERE parent_object_id = OBJECT_ID('dbo.booking_services')
      AND name = 'df_booking_services_reward_multiplier'
)
BEGIN
    ALTER TABLE dbo.booking_services
        ADD CONSTRAINT df_booking_services_reward_multiplier DEFAULT 1.0 FOR reward_multiplier;
END;
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.check_constraints
    WHERE parent_object_id = OBJECT_ID('dbo.booking_services')
      AND name = 'chk_booking_services_reward_multiplier'
)
BEGIN
    ALTER TABLE dbo.booking_services WITH CHECK
        ADD CONSTRAINT chk_booking_services_reward_multiplier
        CHECK (
            reward_multiplier BETWEEN 1.0 AND 5.0
            AND reward_multiplier * 2 = FLOOR(reward_multiplier * 2)
        );
END;
GO
