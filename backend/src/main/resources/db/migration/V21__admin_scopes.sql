ALTER TABLE user_account ADD COLUMN admin_scope VARCHAR(16);
UPDATE user_account SET admin_scope = 'SUPER_ADMIN' WHERE role = 'ADMIN';
ALTER TABLE user_account ADD CONSTRAINT ck_admin_scope
    CHECK (admin_scope IS NULL OR admin_scope IN ('SUPER_ADMIN', 'REVIEWER', 'MODERATOR'));
