ALTER TABLE dbo.loyalty_transactions
    ADD status VARCHAR(20) NOT NULL
        CONSTRAINT df_loyalty_transactions_status DEFAULT 'POSTED';
GO

ALTER TABLE dbo.loyalty_transactions
    ADD CONSTRAINT chk_loyalty_transactions_status
        CHECK (status IN ('PENDING', 'POSTED', 'REVERSED'));
GO

IF EXISTS (
    SELECT booking_id
    FROM dbo.loyalty_transactions
    WHERE booking_id IS NOT NULL
      AND type = 'EARN'
    GROUP BY booking_id
    HAVING COUNT(*) > 1
)
    THROW 51000, 'Duplicate booking-linked EARN transactions must be resolved before migration', 1;
GO

CREATE UNIQUE INDEX ux_loyalty_transactions_booking_earn
    ON dbo.loyalty_transactions(booking_id)
    WHERE booking_id IS NOT NULL
      AND type = 'EARN';
GO
