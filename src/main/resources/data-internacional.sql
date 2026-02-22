-- Datos iniciales para Banco Internacional (MySQL)
-- Compatible con schema-internacional-mysql.sql

INSERT IGNORE INTO cuenta (numero_cuenta, titular, saldo) VALUES
('BI-001', 'Laura Sánchez', 8000.00),
('BI-002', 'Pedro López', 3000.00),
('BI-003', 'Sofia Hernández', 12000.00),
('BI-004', 'Diego Torres', 6000.00);