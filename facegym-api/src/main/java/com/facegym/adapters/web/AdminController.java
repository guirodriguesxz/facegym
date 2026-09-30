package com.facegym.adapters.web;

import com.facegym.application.GestaoDeAlunos;
import com.facegym.application.GestaoDePlanos;
import com.facegym.application.PinDoAluno;
import com.facegym.adapters.jdbc.TotensJdbc;
import com.facegym.application.port.Relogio;
import com.facegym.application.port.RegistroDeAcessos;
import com.facegym.domain.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class AdminController {

    public record AlunoRequest(@NotBlank @Size(max = 120) String nome, @NotBlank String cpf, @Email String email) {}
    public record AlunoResponse(UUID id, String nome, String cpf, String email, boolean bloqueado,
                                String motivoBloqueio, Instant consentimentoBiometricoEm) {
        static AlunoResponse de(Aluno a) {
            return new AlunoResponse(a.id(), a.nome(), a.cpf().valor(), a.email(), a.bloqueado(),
                    a.motivoBloqueio(), a.consentimentoBiometricoEm());
        }
    }
    public record PinRequest(@NotBlank @Pattern(regexp = "\\d{4,6}", message = "deve ter de 4 a 6 dígitos") String pin) {}
    public record TotemRequest(@NotBlank @Size(max = 80) String nome) {}
    public record BloqueioRequest(@NotBlank @Size(max = 200) String motivo) {}
    public record PlanoRequest(@NotBlank @Size(max = 80) String nome, @NotNull @PositiveOrZero BigDecimal preco,
                               @NotEmpty Set<DayOfWeek> dias, @NotNull LocalTime inicio, @NotNull LocalTime fim,
                               @Positive Integer acessosPorSemana) {}
    public record MatriculaRequest(@NotNull UUID alunoId, @NotNull UUID planoId, @NotNull LocalDate inicio,
                                   @NotNull LocalDate vencimento) {}

    private final GestaoDeAlunos alunos;
    private final GestaoDePlanos planos;
    private final RegistroDeAcessos acessos;
    private final PinDoAluno pins;
    private final TotensJdbc totens;
    private final Relogio relogio;

    public AdminController(GestaoDeAlunos alunos, GestaoDePlanos planos, RegistroDeAcessos acessos, PinDoAluno pins,
                           TotensJdbc totens, Relogio relogio) {
        this.alunos = alunos;
        this.planos = planos;
        this.acessos = acessos;
        this.pins = pins;
        this.totens = totens;
        this.relogio = relogio;
    }

    @PutMapping("/alunos/{id}/pin")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void definirPin(@PathVariable UUID id, @Valid @RequestBody PinRequest r) { pins.definir(id, r.pin()); }

    @GetMapping("/totens")
    public List<TotensJdbc.Totem> listarTotens() { return totens.listar(); }

    /** Única vez em que o token aparece: configure o totem com ele. */
    @PostMapping("/totens")
    @ResponseStatus(HttpStatus.CREATED)
    public TotensJdbc.TotemNovo cadastrarTotem(@Valid @RequestBody TotemRequest r) { return totens.criar(r.nome(), relogio.agora()); }

    @DeleteMapping("/totens/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removerTotem(@PathVariable UUID id) { totens.remover(id); }

    @GetMapping("/alunos")
    public List<AlunoResponse> listarAlunos() { return alunos.listar().stream().map(AlunoResponse::de).toList(); }

    @PostMapping("/alunos")
    @ResponseStatus(HttpStatus.CREATED)
    public AlunoResponse cadastrarAluno(@Valid @RequestBody AlunoRequest r) {
        return AlunoResponse.de(alunos.cadastrar(r.nome(), r.cpf(), r.email()));
    }

    @PostMapping("/alunos/{id}/bloqueio")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void bloquear(@PathVariable UUID id, @Valid @RequestBody BloqueioRequest r) { alunos.bloquear(id, r.motivo()); }

    @DeleteMapping("/alunos/{id}/bloqueio")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void desbloquear(@PathVariable UUID id) { alunos.desbloquear(id); }

    @PostMapping("/alunos/{id}/consentimento")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void consentimento(@PathVariable UUID id) { alunos.registrarConsentimento(id); }

    @PutMapping("/alunos/{id}/biometria")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cadastrarBiometria(@PathVariable UUID id, @RequestParam("foto") MultipartFile foto) throws IOException {
        alunos.cadastrarBiometria(id, foto.getBytes());
    }

    @DeleteMapping("/alunos/{id}/biometria")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removerBiometria(@PathVariable UUID id) { alunos.removerBiometria(id); }

    @GetMapping("/planos")
    public List<Plano> listarPlanos() { return planos.listar(); }

    @PostMapping("/planos")
    @ResponseStatus(HttpStatus.CREATED)
    public Plano cadastrarPlano(@Valid @RequestBody PlanoRequest r) {
        return planos.cadastrar(r.nome(), r.preco(), r.dias(), r.inicio(), r.fim(), r.acessosPorSemana());
    }

    @PostMapping("/matriculas")
    @ResponseStatus(HttpStatus.CREATED)
    public Matricula matricular(@Valid @RequestBody MatriculaRequest r) {
        return planos.matricular(r.alunoId(), r.planoId(), r.inicio(), r.vencimento());
    }

    @GetMapping("/acessos")
    public List<Acesso> acessos(@RequestParam(defaultValue = "50") @Min(1) @Max(500) int limite) {
        return acessos.recentes(Math.min(Math.max(limite, 1), 500));
    }
}
