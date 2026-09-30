package com.facegym.adapters.web;

import com.facegym.adapters.biometria.BiometriaHttpClient;
import com.facegym.application.VisitantesTemporarios;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/demo")
public class DemoController {
    private final VisitantesTemporarios visitantes;
    private final BiometriaHttpClient biometria;

    public DemoController(VisitantesTemporarios visitantes, BiometriaHttpClient biometria) {
        this.visitantes = visitantes;
        this.biometria = biometria;
    }

    @PostMapping("/visitantes")
    @ResponseStatus(HttpStatus.CREATED)
    public VisitantesTemporarios.Visitante criar(@RequestParam("foto") MultipartFile foto,
                                                 @RequestParam(defaultValue = "false") boolean consentimento) throws IOException {
        return visitantes.criar(foto.getBytes(), consentimento);
    }

    @DeleteMapping("/visitantes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remover(@PathVariable UUID id,
                        @RequestHeader(value = "X-Visitante-Segredo", required = false) String segredo) {
        visitantes.remover(id, segredo);
    }

    public record Status(String biometria) {}

    /** O totem chama até receber PRONTA; enquanto isso mostra "iniciando". */
    @PostMapping("/aquecer")
    public Status aquecer() { return new Status(biometria.pronta() ? "PRONTA" : "ACORDANDO"); }
}
