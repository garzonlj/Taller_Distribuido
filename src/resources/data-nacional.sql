-- Datos iniciales para Banco Nacional (PostgreSQL)
-- Compatible con Schema-nacional-PostgreSQL.sql

INSERT INTO cuenta (numero_cuenta, titular, saldo) VALUES
                                                       ('BN-001', 'Juan Pérez', 5000.00),
                                                       ('BN-002', 'María García', 10000.00),
                                                       ('BN-003', 'Carlos Rodríguez', 2500.00),
                                                       ('BN-004', 'Ana Martínez', 15000.00)
    ON CONFLICT (numero_cuenta) DO NOTHING;