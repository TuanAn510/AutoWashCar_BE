IF EXISTS (
    SELECT 1 FROM sys.check_constraints
    WHERE parent_object_id = OBJECT_ID('dbo.services')
      AND name = 'chk_services_reward_points_multiplier_rate'
)
BEGIN
    ALTER TABLE dbo.services DROP CONSTRAINT chk_services_reward_points_multiplier_rate;
END;
GO

IF EXISTS (
    SELECT 1 FROM sys.check_constraints
    WHERE parent_object_id = OBJECT_ID('dbo.services')
      AND name = 'chk_services_reward_multiplier'
)
BEGIN
    ALTER TABLE dbo.services DROP CONSTRAINT chk_services_reward_multiplier;
END;
GO

-- Fractional values were briefly supported. Normalize only the current service
-- configuration; booking snapshots remain unchanged for historical consistency.
UPDATE dbo.services
SET reward_multiplier = FLOOR(reward_multiplier)
WHERE reward_multiplier <> FLOOR(reward_multiplier);
GO

UPDATE dbo.services
SET reward_points = CONVERT(INT, FLOOR(FLOOR(price / 10000) * reward_multiplier));
GO

ALTER TABLE dbo.services WITH CHECK
    ADD CONSTRAINT chk_services_reward_multiplier
    CHECK (
        reward_multiplier BETWEEN 1.0 AND 5.0
        AND reward_multiplier = FLOOR(reward_multiplier)
    );
GO

ALTER TABLE dbo.services WITH CHECK
    ADD CONSTRAINT chk_services_reward_points_multiplier_rate
    CHECK (reward_points = CONVERT(INT, FLOOR(FLOOR(price / 10000) * reward_multiplier)));
GO
