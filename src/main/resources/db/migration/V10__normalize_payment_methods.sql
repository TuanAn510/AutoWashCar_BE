SET ANSI_NULLS ON;
GO

SET QUOTED_IDENTIFIER ON;
GO

IF EXISTS (
    SELECT 1
    FROM sys.check_constraints
    WHERE name = 'chk_bookings_payment_method'
      AND parent_object_id = OBJECT_ID('dbo.bookings')
)
BEGIN
    ALTER TABLE dbo.bookings DROP CONSTRAINT chk_bookings_payment_method;
END
GO

UPDATE dbo.bookings
SET payment_method = 'VNPAY'
WHERE payment_method = 'BANK_TRANSFER';
GO

ALTER TABLE dbo.bookings
    ADD CONSTRAINT chk_bookings_payment_method CHECK (payment_method IN ('CASH', 'VNPAY', 'MOMO'));
GO

IF EXISTS (
    SELECT 1
    FROM sys.check_constraints
    WHERE name = 'chk_bookings_payment_status'
      AND parent_object_id = OBJECT_ID('dbo.bookings')
)
BEGIN
    ALTER TABLE dbo.bookings DROP CONSTRAINT chk_bookings_payment_status;
END
GO

ALTER TABLE dbo.bookings
    ADD CONSTRAINT chk_bookings_payment_status CHECK (payment_status IN ('UNPAID', 'PENDING', 'PAID', 'CANCELLED'));
GO
