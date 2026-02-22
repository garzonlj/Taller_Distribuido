package co.edu.javeriana.transferencias.service;

import co.edu.javeriana.transferencias.exception.CuentaNoEncontradaException;
import co.edu.javeriana.transferencias.model.Cuenta;
import co.edu.javeriana.transferencias.model.Movimiento;
import co.edu.javeriana.transferencias.repository.internacional.CuentaInternacionalRepository;
import co.edu.javeriana.transferencias.repository.internacional.MovimientoInternacionalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BancoInternacionalService {

    private final CuentaInternacionalRepository cuentaRepository;
    private final MovimientoInternacionalRepository movimientoRepository;

    private boolean simularFallo = false;

    /**
     * Paso 2 del SAGA: Acredita el monto en la cuenta destino en MySQL.
     * Si esto falla, el orquestador llamará a revertirDebito() en PostgreSQL.
     */
    @Transactional(transactionManager = "internacionalTransactionManager")
    public void acreditar(String numeroCuenta, BigDecimal monto, String referencia) {
        log.info("[SAGA][MySQL] Iniciando crédito. Cuenta: {}, Monto: {}, Ref: {}",
                numeroCuenta, monto, referencia);

        if ("BI-002".equals(numeroCuenta)) {
            throw new RuntimeException("Fallo simulado en acreditación de BI-002");
        }

        Cuenta cuenta = cuentaRepository.findByNumeroCuentaWithLock(numeroCuenta)
                .orElseThrow(() -> new CuentaNoEncontradaException(
                        "Cuenta destino no encontrada en Banco Internacional: " + numeroCuenta));

        if (!cuenta.getActiva()) {
            throw new IllegalStateException("La cuenta destino " + numeroCuenta + " está inactiva");
        }

        BigDecimal saldoAnterior = cuenta.getSaldo();
        BigDecimal saldoNuevo = saldoAnterior.add(monto);
        cuenta.setSaldo(saldoNuevo);
        cuentaRepository.save(cuenta);

        // Registrar movimiento con todos los campos del schema
        Movimiento mov = new Movimiento();
        mov.setCuentaId(cuenta.getId());
        mov.setTipo("CREDITO");
        mov.setMonto(monto);
        mov.setSaldoAnterior(saldoAnterior);
        mov.setSaldoNuevo(saldoNuevo);
        mov.setDescripcion("Transferencia internacional - crédito SAGA");
        mov.setReferenciaTransferencia(referencia);
        movimientoRepository.save(mov);

        log.info("[SAGA][MySQL] Crédito exitoso. Saldo anterior: {}, Nuevo saldo: {}",
                saldoAnterior, saldoNuevo);
    }

    @Transactional(transactionManager = "internacionalTransactionManager", readOnly = true)
    public List<Cuenta> listarCuentas() {
        return cuentaRepository.findByActivaTrue();
    }


}