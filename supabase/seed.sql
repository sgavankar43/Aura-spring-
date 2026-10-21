-- Aura Feature Flag Platform Seed Data
-- Default Admin and Developer accounts

-- Seed Admin User (Password: AdminPassword123!)
INSERT INTO users (id, email, password, name, role)
VALUES (
    '32e0b45a-d947-4c46-a083-ed479570fc96',
    'admin@aura.dev',
    '$2a$10$AmS1DbraTPigetYRMyN0SOzprgmgiNk6.u5V7PsuiJV/GZUKqmPF.',
    'System Administrator',
    'ADMIN'
)
ON CONFLICT (email) DO NOTHING;

-- Seed Developer User (Password: DevPassword123!)
INSERT INTO users (id, email, password, name, role)
VALUES (
    'f5b7167d-c211-47cc-9723-a2f2a261569a',
    'developer@aura.dev',
    '$2a$10$22vnYMeFseLKrUlRZTm6b.eFYyZjjLgO9cYsJt3xxZ.1bC/diLprG',
    'Developer User',
    'DEVELOPER'
)
ON CONFLICT (email) DO NOTHING;
