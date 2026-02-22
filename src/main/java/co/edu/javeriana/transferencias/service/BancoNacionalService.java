package co.edu.javeriana.transferencias.service;

import co.edu.javeriana.transferencias.exception.SaldoInsuficienteException;
import co.edu.javeriana.transferencias.exception.CuentaNoEncontradaException;
import co.edu.javeriana.transferencias.model.Cuenta;
import co.edu.javeriana.transferencias.model.Movimiento;
import co.edu.javeriana.transferencias.repository.nacional.CuentaNacionalRepository;
import co.edu.javeriana.transferencias.repository.nacional.MovimientoNacionalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BancoNacionalService {

    private final CuentaNacionalRepository cuentaRepository;
    private final MovimientoNacionalRepository movimientoRepository;

    /**
     * Paso 1 del SAGA: Debita el monto de la cuenta origen en PostgreSQL.
     * Usa lock pesimista para evitar condiciones de carrera.
     */
    @Transactional(transactionManager = "nacionalTransactionManager")
    public void debitar(String numeroCuenta, BigDecimal monto, String referencia) {
        log.info("[SAGA][PostgreSQL] Iniciando débito. Cuenta: {}, Monto: {}, Ref: {}",
                numeroCuenta, monto, referencia);

        Cuenta cuenta = cuentaRepository.findByNumeroCuentaWithLock(numeroCuenta)
                .orElseThrow(() -> new CuentaNoEncontradaException(
                        "Cuenta origen no encontrada en Banco Nacional: " + numeroCuenta));

        if (!cuenta.getActiva()) {
            throw new IllegalStateException("La cuenta " + numeroCuenta + " está inactiva");
        }

        if (cuenta.getSaldo().compareTo(monto) < 0) {
            log.warn("[SAGA][PostgreSQL] Saldo insuficiente. Saldo: {}, Monto requerido: {}",
                    cuenta.getSaldo(), monto);
            throw new SaldoInsuficienteException(
                    "Saldo insuficiente en cuenta " + numeroCuenta +
                            ". Saldo: " + cuenta.getSaldo() + ", Requerido: " + monto);
        }

        BigDecimal saldoAnterior = cuenta.getSaldo();
        BigDecimal saldoNuevo = saldoAnterior.subtract(monto);
        cuenta.setSaldo(saldoNuevo);
        cuentaRepository.save(cuenta);

        // Registrar movimiento con todos los campos del schema
        Movimiento mov = new Movimiento();
        mov.setCuentaId(cuenta.getId());
        mov.setTipo("DEBITO");
        mov.setMonto(monto);
        mov.setSaldoAnterior(saldoAnterior);
        mov.setSaldoNuevo(saldoNuevo);
        mov.setDescripcion("Transferencia internacional - débito SAGA");
        mov.setReferenciaTransferencia(referencia);
        movimientoRepository.save(mov);

        log.info("[SAGA][PostgreSQL] Débito exitoso. Saldo anterior: {}, Nuevo saldo: {}",
                saldoAnterior, saldoNuevo);
    }

    /**
     * Transacción compensatoria del SAGA: revierte el débito si falla el crédito en MySQL.
     */
    @Transactional(transactionManager = "nacionalTransactionManager")
    public void revertirDebito(String numeroCuenta, BigDecimal monto, String referencia) {
        log.warn("[SAGA][COMPENSACIÓN][PostgreSQL] Revirtiendo débito. Cuenta: {}, Monto: {}, Ref: {}",
                numeroCuenta, monto, referencia);

        Cuenta cuenta = cuentaRepository.findByNumeroCuentaWithLock(numeroCuenta)
                .orElseThrow(() -> new CuentaNoEncontradaException(
                        "Cuenta no encontrada durante compensación: " + numeroCuenta));

        BigDecimal saldoAnterior = cuenta.getSaldo();
        BigDecimal saldoNuevo = saldoAnterior.add(monto);
        cuenta.setSaldo(saldoNuevo);
        cuentaRepository.save(cuenta);

        // Registrar la reversión como movimiento CREDITO
        Movimiento mov = new Movimiento();
        mov.setCuentaId(cuenta.getId());
        mov.setTipo("CREDITO");
        mov.setMonto(monto);
        mov.setSaldoAnterior(saldoAnterior);
        mov.setSaldoNuevo(saldoNuevo);
        mov.setDescripcion("COMPENSACIÓN SAGA - reversión de débito");
        mov.setReferenciaTransferencia(referencia + "-REVERSION");
        movimientoRepository.save(mov);

        log.warn("[SAGA][COMPENSACIÓN][PostgreSQL] Débito revertido. Saldo restaurado: {} → {}",
                saldoAnterior, saldoNuevo);
    }

    @Transactional(transactionManager = "nacionalTransactionManager", readOnly = true)
    public List<Cuenta> listarCuentas() {
        return cuentaRepository.findByActivaTrue();
    }


}