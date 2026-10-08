-- Development-only seed for the local purchase demo.
-- Login: admin / AdminPass123!
-- Do not use this credential outside an isolated development environment.
INSERT INTO mall_user
    (id, username, password_hash, nickname, role, status, created_at, updated_at)
VALUES
    (900000000000000001, 'admin',
     '$2a$10$6I8SbRZ0hlWMIxB0dzvkRe9G0ShLR8CmK5iaC7gCcnfrWoaq3O0t6',
     'NovaMall Admin', 'ADMIN', 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE
    password_hash = VALUES(password_hash),
    nickname = VALUES(nickname),
    role = 'ADMIN',
    status = 1,
    updated_at = NOW();
