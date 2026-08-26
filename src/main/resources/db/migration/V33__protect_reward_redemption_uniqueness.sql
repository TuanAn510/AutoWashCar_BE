IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.reward_redemptions')
      AND name = 'ux_reward_redemptions_customer_reward'
)
BEGIN
    CREATE UNIQUE INDEX ux_reward_redemptions_customer_reward
        ON dbo.reward_redemptions(customer_id, reward_id);
END;
GO
