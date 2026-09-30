package com.facegym.adapters.web;

import com.facegym.application.RealizarCheckIn;
import com.facegym.application.ResultadoCheckIn;
import com.facegym.application.ResultadoCheckIn.*;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class CheckInController {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Resposta(String status, String nome, String motivo, String token, Boolean vivacidade) {
        static Resposta de(ResultadoCheckIn r) {
            return switch (r) {
                case Liberado l -> new Resposta("LIBERADO", l.nome(), null, null, l.provaDeVida() ? true : null);
                case Negado n -> new Resposta("NEGADO", n.nome(), n.motivo(), null, null);
                case ConfirmarCpf c -> new Resposta("CONFIRMAR_CPF", null, null, c.token(), null);
                case NaoReconhecido x -> new Resposta("NAO_RECONHECIDO", null, null, null, null);
                case BiometriaIndisponivel x -> new Resposta("BIOMETRIA_INDISPONIVEL", null, null, null, null);
                case ProvaDeVidaReprovada p -> new Resposta("PROVA_DE_VIDA_REPROVADA", null, p.motivo(), null, null);
            };
        }
    }

    public record CpfRequest(@NotBlank String cpf) {}

    private final RealizarCheckIn checkIn;

    public CheckInController(RealizarCheckIn checkIn) { this.checkIn = checkIn; }

    @PostMapping("/check-ins/desafios")
    public Map<String, String> desafio() {
        var d = checkIn.novoDesafio();
        return Map.of("token", d.token(), "lado", d.lado().name());
    }

    @PostMapping("/check-ins")
    public Resposta porFoto(@RequestParam("foto") MultipartFile foto,
                            @RequestParam(value = "fotoVirada", required = false) MultipartFile virada,
                            @RequestParam(value = "desafio", required = false) String desafio) throws IOException {
        if (desafio != null || virada != null) {
            return Resposta.de(checkIn.porFotoComDesafio(foto.getBytes(), virada == null ? null : virada.getBytes(), desafio));
        }
        return Resposta.de(checkIn.porFoto(foto.getBytes()));
    }

    @PostMapping("/check-ins/cpf")
    public Resposta porCpf(@Valid @RequestBody CpfRequest req) {
        return Resposta.de(checkIn.porCpf(req.cpf()));
    }

    @PostMapping("/check-ins/{token}/cpf")
    public Resposta confirmar(@PathVariable String token, @Valid @RequestBody CpfRequest req) {
        return Resposta.de(checkIn.confirmarCpf(token, req.cpf()));
    }

    @PostMapping("/demo/identificar")
    public Map<String, String> demo(@RequestParam("foto") MultipartFile foto) throws IOException {
        var nome = checkIn.demo(foto.getBytes()).orElse(null);
        var body = new java.util.HashMap<String, String>();
        body.put("nome", nome);
        return body;
    }
}
