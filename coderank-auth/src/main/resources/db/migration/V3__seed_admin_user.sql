-- V3__seed_admin_user.sql
-- Seeds a default ROLE_ADMIN user for system operations.
-- Password: Admin@1234 (BCrypt strength 12 — rotate in production via secrets manager)

INSERT INTO users (id, username, email, password_hash, role, created_at)
VALUES (
           gen_random_uuid(),
           'admin',
           'admin@coderank.io',
           '$2a$12$7QFoAORY5F1fBPuqKjHM5.vCf5k9ZGq5kIwSTLSYIeqnkgF2oNVDu',
           'ROLE_ADMIN',
           now()
       )
ON CONFLICT (email) DO NOTHING;