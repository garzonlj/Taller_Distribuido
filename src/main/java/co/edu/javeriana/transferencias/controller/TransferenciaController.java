package co.edu.javeriana.transferencias.controller;

import co.edu.javeriana.transferencias.dto.TransferenciaRequest;
import co.edu.javeriana.transferencias.dto.TransferenciaResponse;
import co.edu.javeriana.transferencias.model.Cuenta;
import co.edu.javeriana.transferencias.service.BancoInternacionalService;
import co.edu.javeriana.transferencias.service.BancoNacionalService;
import co.edu.javeriana.transferencias.service.TransferenciaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TransferenciaController {

    private final TransferenciaService transferenciaService;
    private final BancoNacionalService bancoNacionalService;
    private final BancoInternacionalService bancoInternacionalService;

    /**
     * POST /api/transferencias
     * Ejecuta una transferencia interbancaria usando el patrón SAGA
     */
    @PostMapping("/transferencias")
    public ResponseEntity<TransferenciaResponse> realizarTransferencia(
            @Valid @RequestBody TransferenciaRequest request) {
        log.info("POST /api/transferencias - Origen: {}, Destino: {}, Monto: {}",
                request.getCuentaOrigen(), request.getCuentaDestino(), request.getMonto());

        TransferenciaResponse response = transferenciaService.ejecutarTransferencia(request);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/cuentas/nacional
     * Lista las cuentas del Banco Nacional (PostgreSQL)
     */
    @GetMapping("/cuentas/nacional")
    public ResponseEntity<List<Cuenta>> cuentasNacional() {
        return ResponseEntity.ok(bancoNacionalService.listarCuentas());
    }

    /**
     * GET /api/cuentas/internacional
     * Lista las cuentas del Banco Internacional (MySQL)
     */
    @GetMapping("/cuentas/internacional")
    public ResponseEntity<List<Cuenta>> cuentasInternacional() {
        return ResponseEntity.ok(bancoInternacionalService.listarCuentas());
    }



}
