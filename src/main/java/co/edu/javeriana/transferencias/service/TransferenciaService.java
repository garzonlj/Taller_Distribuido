package co.edu.javeriana.transferencias.service;

import co.edu.javeriana.transferencias.dto.TransferenciaRequest;
import co.edu.javeriana.transferencias.dto.TransferenciaResponse;
import co.edu.javeriana.transferencias.model.EstadoTransferencia;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferenciaService {

    private final BancoNacionalService bancoNacionalService;
    private final BancoInternacionalService bancoInternacionalService;

    /**
     * Ejecuta el SAGA de transferencia completo
     * Cada paso tiene su propia transacción local
     * en caso de fallo en el paso 2, se ejecuta la compensación del paso 1
     */
    public TransferenciaResponse ejecutarTransferencia(TransferenciaRequest request) {
        // Generar referencia única para rastrear toda la saga
        String referencia = "TRF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        LocalDateTime inicio = LocalDateTime.now();


        log.info("INICIANDO SAGA - Ref: {}", referencia);
        log.info("Origen : {} (Banco Nacional - PostgreSQL)", request.getCuentaOrigen());
        log.info("Destino: {} (Banco Internacional - MySQL)", request.getCuentaDestino());
        log.info("Monto  : {}", request.getMonto());


        EstadoTransferencia estado = EstadoTransferencia.INICIADA;
        String mensajeError = null;


        try {
            log.info("[SAGA][PASO 1] Ejecutando débito en PostgreSQL...");
            bancoNacionalService.debitar(
                    request.getCuentaOrigen(),
                    request.getMonto(),
                    referencia
            );
            estado = EstadoTransferencia.DEBITO_COMPLETADO;
            log.info("[SAGA][PASO 1] Débito completado. Estado: {}", estado);

        } catch (Exception e) {
            estado = EstadoTransferencia.FALLIDA;
            mensajeError = "Falló el débito en Banco Nacional: " + e.getMessage();
            log.error("[SAGA][PASO 1] FALLO. Estado: {}. Error: {}", estado, e.getMessage());

            return TransferenciaResponse.builder()
                    .referencia(referencia)
                    .estado(estado)
                    .cuentaOrigen(request.getCuentaOrigen())
                    .cuentaDestino(request.getCuentaDestino())
                    .monto(request.getMonto())
                    .mensaje(mensajeError)
                    .fechaInicio(inicio)
                    .fechaFin(LocalDateTime.now())
                    .build();
        }


        try {
            log.info("[SAGA][PASO 2] Ejecutando crédito en MySQL...");
            bancoInternacionalService.acreditar(
                    request.getCuentaDestino(),
                    request.getMonto(),
                    referencia
            );
            estado = EstadoTransferencia.CREDITO_COMPLETADO;
            log.info("[SAGA][PASO 2] Crédito completado. Estado: {}", estado);

        } catch (Exception e) {
            mensajeError = "Falló el crédito en Banco Internacional: " + e.getMessage();
            log.error("[SAGA][PASO 2] FALLO. Error: {}. Iniciando COMPENSACIÓN...", e.getMessage());


            try {
                log.warn("[SAGA][COMPENSACIÓN] Revirtiendo débito en PostgreSQL...");
                bancoNacionalService.revertirDebito(
                        request.getCuentaOrigen(),
                        request.getMonto(),
                        referencia
                );
                estado = EstadoTransferencia.REVERTIDA;
                log.warn("[SAGA][COMPENSACIÓN] Débito revertido exitosamente. Estado: {}", estado);

            } catch (Exception compensacionEx) {
                // Situación crítica: el débito ocurrió pero no pudimos revertirlo
                // En un sistema real, esto requeriría intervención manual o un sistema de retry
                estado = EstadoTransferencia.FALLIDA;
                mensajeError = "ERROR CRÍTICO: Fallo en compensación. " +
                        "Débito no revertido. Requiere intervención manual. Ref: " + referencia;
                log.error("[SAGA][COMPENSACIÓN] RROR CRÍTICO: {}", mensajeError);
                log.error("[SAGA][COMPENSACIÓN] Error original: {}", e.getMessage());
                log.error("[SAGA][COMPENSACIÓN] Error compensación: {}", compensacionEx.getMessage());
            }

            return TransferenciaResponse.builder()
                    .referencia(referencia)
                    .estado(estado)
                    .cuentaOrigen(request.getCuentaOrigen())
                    .cuentaDestino(request.getCuentaDestino())
                    .monto(request.getMonto())
                    .mensaje(mensajeError)
                    .fechaInicio(inicio)
                    .fechaFin(LocalDateTime.now())
                    .build();
        }


        estado = EstadoTransferencia.COMPLETADA;
        log.info("SAGA COMPLETADO EXITOSAMENTE");
        log.info("Ref: {}", referencia);


        return TransferenciaResponse.builder()
                .referencia(referencia)
                .estado(estado)
                .cuentaOrigen(request.getCuentaOrigen())
                .cuentaDestino(request.getCuentaDestino())
                .monto(request.getMonto())
                .mensaje("Transferencia completada exitosamente")
                .fechaInicio(inicio)
                .fechaFin(LocalDateTime.now())
                .build();
    }
}
