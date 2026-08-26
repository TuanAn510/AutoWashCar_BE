IF COL_LENGTH('dbo.bookings', 'check_in_at') IS NULL
BEGIN
    ALTER TABLE dbo.bookings
        ADD check_in_at DATETIME2(6) NULL;
END
GO

IF COL_LENGTH('dbo.bookings', 'assigned_staff_id') IS NULL
BEGIN
    ALTER TABLE dbo.bookings
        ADD assigned_staff_id BIGINT NULL;
END
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.foreign_keys
    WHERE name = 'fk_bookings_assigned_staff'
      AND parent_object_id = OBJECT_ID('dbo.bookings')
)
BEGIN
    ALTER TABLE dbo.bookings
        ADD CONSTRAINT fk_bookings_assigned_staff FOREIGN KEY (assigned_staff_id) REFERENCES dbo.users(id);
END
GO

IF EXISTS (
    SELECT 1
    FROM sys.check_constraints
    WHERE name = 'chk_users_role'
      AND parent_object_id = OBJECT_ID('dbo.users')
)
BEGIN
    ALTER TABLE dbo.users DROP CONSTRAINT chk_users_role;
END
GO

ALTER TABLE dbo.users
    ADD CONSTRAINT chk_users_role CHECK (role IN ('ROLE_CUSTOMER', 'ROLE_STAFF', 'ROLE_ADMIN'));
GO
