IF COL_LENGTH('dbo.loyalty_transactions', 'posted_at') IS NULL
BEGIN
    ALTER TABLE dbo.loyalty_transactions ADD posted_at DATETIME2 NULL;
END;
GO

UPDATE dbo.loyalty_transactions
SET posted_at = created_at
WHERE status = 'POSTED'
  AND posted_at IS NULL;
GO

DELETE lot
FROM dbo.point_lots lot
JOIN dbo.loyalty_transactions transaction_row
    ON transaction_row.id = lot.earn_transaction_id
WHERE transaction_row.status <> 'POSTED';
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.check_constraints
    WHERE parent_object_id = OBJECT_ID('dbo.loyalty_transactions')
      AND name = 'chk_loyalty_transactions_posted_at'
)
BEGIN
    ALTER TABLE dbo.loyalty_transactions
        ADD CONSTRAINT chk_loyalty_transactions_posted_at
        CHECK (status <> 'POSTED' OR posted_at IS NOT NULL);
END;
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.loyalty_transactions')
      AND name = 'ix_loyalty_transactions_customer_posted_at'
)
BEGIN
    CREATE INDEX ix_loyalty_transactions_customer_posted_at
        ON dbo.loyalty_transactions(customer_id, type, status, posted_at)
        INCLUDE (points, booking_id);
END;
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE object_id = OBJECT_ID('dbo.loyalty_transactions')
      AND name = 'ux_loyalty_transactions_booking_earn'
)
BEGIN
    CREATE UNIQUE INDEX ux_loyalty_transactions_booking_earn
        ON dbo.loyalty_transactions(booking_id)
        WHERE booking_id IS NOT NULL AND type = 'EARN';
END;
GO
