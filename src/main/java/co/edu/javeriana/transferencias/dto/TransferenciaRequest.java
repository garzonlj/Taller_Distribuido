package co.edu.javeriana.transferencias.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class TransferenciaRequest {

    @NotBlank(message = "La cuenta origen es obligatoria")
    private String cuentaOrigen;

    @NotBlank(message = "La cuenta destino es obligatoria")
    private String cuentaDestino;

    @NotNull(message = "El monto es obligatorio")
    @DecimalMin(value = "0.01", message = "El monto debe ser mayor a 0")
    private BigDecimal monto;

    private String descripcion;
}
