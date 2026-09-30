package com.facegym.adapters.web;

import com.facegym.application.RealizarCheckIn;
import com.facegym.application.ResultadoCheckIn;
import com.facegym.application.ResultadoCheckIn.*;
import com.facegym.application.port.Totens;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1")
public class CheckInController {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Resposta(String status, String nome, String motivo, String token) {
        static Resposta de(ResultadoCheckIn r) {
            return switch (r) {
                case Liberado l -> new Resposta("LIBERADO", l.nome(), null, null);
                case Negado n -> new Resposta("NEGADO", n.nome(), n.motivo(), null);
                case ConfirmarCpf c -> new Resposta("CONFIRMAR_CPF", null, null, c.token());
                case NaoReconhecido x -> new Resposta("NAO_RECONHECIDO", null, null, null);
                case BiometriaIndisponivel x -> new Resposta("BIOMETRIA_INDISPONIVEL", null, null, null);
            };
        }
    }

    /** pin: exigido para aluno real (não para os fictícios da demo). */
    public record CpfRequest(@NotBlank String cpf, String pin) {}
    public record ConfirmacaoRequest(@NotBlank String cpf) {}

    static final String TOTEM = "X-Totem-Token";

    private final RealizarCheckIn checkIn;
    private final Totens totens;

    public CheckInController(RealizarCheckIn checkIn, Totens totens) {
        this.checkIn = checkIn;
        this.totens = totens;
    }

    @PostMapping("/check-ins")
    public Resposta porFoto(@RequestParam("foto") MultipartFile foto,
                            @RequestParam(value = "virado", required = false) MultipartFile virado,
                            @RequestParam(value = "desafio", required = false) String desafio,
                            @RequestHeader(value = TOTEM, required = false) String totem) throws IOException {
        return Resposta.de(checkIn.porFoto(foto.getBytes(), virado == null ? null : virado.getBytes(), desafio,
                totens.autenticado(totem)));
    }

    /** O totem pede antes de fotografar e mostra para que lado virar o rosto. */
    @PostMapping("/check-ins/desafio")
    public RealizarCheckIn.Desafio desafio() {
        return checkIn.novoDesafio();
    }

    @PostMapping("/check-ins/cpf")
    public Resposta porCpf(@Valid @RequestBody CpfRequest req, @RequestHeader(value = TOTEM, required = false) String totem) {
        return Resposta.de(checkIn.porCpf(req.cpf(), req.pin(), totens.autenticado(totem)));
    }

    @PostMapping("/check-ins/{token}/cpf")
    public Resposta confirmar(@PathVariable String token, @Valid @RequestBody ConfirmacaoRequest req) {
        return Resposta.de(checkIn.confirmarCpf(token, req.cpf()));
    }
}
