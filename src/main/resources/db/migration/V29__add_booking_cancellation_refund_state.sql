ALTER TABLE dbo.bookings
    ADD cancellation_reason VARCHAR(40) NULL,
        refund_required BIT NOT NULL
            CONSTRAINT df_bookings_refund_required DEFAULT 0;
GO

ALTER TABLE dbo.bookings
    ADD CONSTRAINT chk_bookings_cancellation_reason
        CHECK (cancellation_reason IS NULL OR cancellation_reason IN ('STORE_NOT_CONFIRMED'));
GO
