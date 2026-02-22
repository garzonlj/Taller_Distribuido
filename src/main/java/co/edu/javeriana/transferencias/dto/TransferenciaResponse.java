package co.edu.javeriana.transferencias.dto;

import co.edu.javeriana.transferencias.model.EstadoTransferencia;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class TransferenciaResponse {
    private String referencia;
    private EstadoTransferencia estado;
    private String cuentaOrigen;
    private String cuentaDestino;
    private BigDecimal monto;
    private String mensaje;
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFin;
}
