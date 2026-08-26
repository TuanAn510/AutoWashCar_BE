IF COL_LENGTH('dbo.rewards', 'min_order_amount') IS NULL
BEGIN
    ALTER TABLE dbo.rewards
        ADD min_order_amount DECIMAL(12,2) NULL;
END
GO

IF COL_LENGTH('dbo.rewards', 'max_discount_amount') IS NULL
BEGIN
    ALTER TABLE dbo.rewards
        ADD max_discount_amount DECIMAL(12,2) NULL;
END
GO

IF COL_LENGTH('dbo.rewards', 'quantity') IS NULL
BEGIN
    ALTER TABLE dbo.rewards
        ADD quantity INT NULL;
END
GO

IF COL_LENGTH('dbo.rewards', 'expired_at') IS NULL
BEGIN
    ALTER TABLE dbo.rewards
        ADD expired_at DATETIME2(6) NULL;
END
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.check_constraints
    WHERE name = 'chk_rewards_min_order_amount'
      AND parent_object_id = OBJECT_ID('dbo.rewards')
)
BEGIN
    ALTER TABLE dbo.rewards
        ADD CONSTRAINT chk_rewards_min_order_amount CHECK (min_order_amount IS NULL OR min_order_amount >= 0);
END
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.check_constraints
    WHERE name = 'chk_rewards_max_discount_amount'
      AND parent_object_id = OBJECT_ID('dbo.rewards')
)
BEGIN
    ALTER TABLE dbo.rewards
        ADD CONSTRAINT chk_rewards_max_discount_amount CHECK (max_discount_amount IS NULL OR max_discount_amount >= 0);
END
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.check_constraints
    WHERE name = 'chk_rewards_quantity'
      AND parent_object_id = OBJECT_ID('dbo.rewards')
)
BEGIN
    ALTER TABLE dbo.rewards
        ADD CONSTRAINT chk_rewards_quantity CHECK (quantity IS NULL OR quantity >= 0);
END
GO
