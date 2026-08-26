IF COL_LENGTH('dbo.bookings', 'assigned_staff_id') IS NULL
BEGIN
    ALTER TABLE dbo.bookings
        ADD assigned_staff_id BIGINT NULL;

    ALTER TABLE dbo.bookings
        ADD CONSTRAINT fk_bookings_assigned_staff FOREIGN KEY (assigned_staff_id) REFERENCES dbo.users(id);
END
GO

IF COL_LENGTH('dbo.bookings', 'payment_method') IS NULL
BEGIN
    ALTER TABLE dbo.bookings
        ADD payment_method VARCHAR(20) NOT NULL CONSTRAINT df_bookings_payment_method DEFAULT 'CASH';
END
GO

IF COL_LENGTH('dbo.bookings', 'payment_status') IS NULL
BEGIN
    ALTER TABLE dbo.bookings
        ADD payment_status VARCHAR(20) NOT NULL CONSTRAINT df_bookings_payment_status DEFAULT 'UNPAID';
END
GO

IF COL_LENGTH('dbo.bookings', 'paid_at') IS NULL
BEGIN
    ALTER TABLE dbo.bookings
        ADD paid_at DATETIME2(6) NULL;
END
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.check_constraints
    WHERE name = 'chk_bookings_payment_method'
      AND parent_object_id = OBJECT_ID('dbo.bookings')
)
BEGIN
    ALTER TABLE dbo.bookings
        ADD CONSTRAINT chk_bookings_payment_method CHECK (payment_method IN ('CASH', 'VNPAY', 'MOMO'));
END
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.check_constraints
    WHERE name = 'chk_bookings_payment_status'
      AND parent_object_id = OBJECT_ID('dbo.bookings')
)
BEGIN
    ALTER TABLE dbo.bookings
        ADD CONSTRAINT chk_bookings_payment_status CHECK (payment_status IN ('UNPAID', 'PENDING', 'PAID', 'CANCELLED'));
END
GO
