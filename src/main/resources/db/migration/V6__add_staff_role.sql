-- V6: Add ROLE_STAFF to users role check constraint
ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_users_role;
ALTER TABLE users ADD CONSTRAINT chk_users_role CHECK (role IN ('ROLE_CUSTOMER', 'ROLE_STAFF', 'ROLE_ADMIN'));