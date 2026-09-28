package com.facegym.application;

import com.facegym.adapters.memoria.CheckInsPendentesEmMemoria;
import com.facegym.application.port.*;
import com.facegym.domain.*;

import java.time.*;
import java.util.*;

/** Implementações em memória das portas, para testar casos de uso sem Spring. */
public class Fakes {

    public static class ReconhecimentoFake implements ReconhecimentoFacial {
        public Identificacao proxima = Identificacao.ninguem();
        public boolean fora = false;
        public final Map<UUID, byte[]> cadastrados = new HashMap<>();

        public Identificacao identificar(byte[] foto) {
            if (fora) throw new ReconhecimentoIndisponivel("fora do ar", null);
            return proxima;
        }
        public Identificacao compararDemo(byte[] foto) { return identificar(foto); }
        public void cadastrar(UUID id, byte[] foto) {
            if (fora) throw new ReconhecimentoIndisponivel("fora do ar", null);
            cadastrados.put(id, foto);
        }
        public void remover(UUID id) {
            if (fora) throw new ReconhecimentoIndisponivel("fora do ar", null);
            cadastrados.remove(id);
        }
    }

    public static class AlunosFake implements Alunos {
        public final Map<UUID, Aluno> dados = new LinkedHashMap<>();
        public Optional<Aluno> porId(UUID id) { return Optional.ofNullable(dados.get(id)); }
        public Optional<Aluno> porCpf(Cpf cpf) { return dados.values().stream().filter(a -> a.cpf().equals(cpf)).findFirst(); }
        public void salvar(Aluno a) { dados.put(a.id(), a); }
        public List<Aluno> todos() { return List.copyOf(dados.values()); }
        public java.util.function.Consumer<UUID> aoRemover = id -> {};
        public void remover(UUID id) { dados.remove(id); aoRemover.accept(id); }
    }

    public static class PlanosFake implements Planos {
        public final Map<UUID, Plano> dados = new LinkedHashMap<>();
        public Optional<Plano> porId(UUID id) { return Optional.ofNullable(dados.get(id)); }
        public void salvar(Plano p) { dados.put(p.id(), p); }
        public List<Plano> todos() { return List.copyOf(dados.values()); }
    }

    public static class MatriculasFake implements Matriculas {
        public final List<Matricula> dados = new ArrayList<>();
        public Optional<Matricula> vigente(UUID alunoId, LocalDate dia) {
            return dados.stream().filter(m -> m.alunoId().equals(alunoId) && m.vigenteEm(dia))
                    .max(Comparator.comparing(Matricula::vencimento));
        }
        public void salvar(Matricula m) { dados.add(m); }
    }

    public static class AcessosFake implements RegistroDeAcessos {
        public final List<Acesso> dados = new ArrayList<>();
        public void registrar(Acesso a) { dados.add(a); }
        public long liberadosDesde(UUID alunoId, Instant desde) {
            return dados.stream().filter(a -> alunoId.equals(a.alunoId()) && a.resultado() == ResultadoAcesso.LIBERADO
                    && !a.dataHora().isBefore(desde)).count();
        }
        public List<Acesso> recentes(int limite) {
            return dados.reversed().stream().limit(limite).toList();
        }
    }

    public static class RelogioFake implements Relogio {
        public Instant agora;
        public RelogioFake(LocalDateTime local) { set(local); }
        public void set(LocalDateTime local) { agora = local.atZone(fuso()).toInstant(); }
        public Instant agora() { return agora; }
        public ZoneId fuso() { return ZoneId.of("America/Sao_Paulo"); }
    }

    public static class VisitantesFake implements Visitantes {
        public record Registro(Instant criadoEm, Instant expiraEm) {}
        public final Map<UUID, Registro> dados = new LinkedHashMap<>();
        public final List<Instant> criacoes = new ArrayList<>(); // histórico para o limite por hora
        public void registrar(UUID id, Instant criadoEm, Instant expiraEm) {
            dados.put(id, new Registro(criadoEm, expiraEm));
            criacoes.add(criadoEm);
        }
        public boolean existe(UUID id) { return dados.containsKey(id); }
        public List<UUID> expiradosAte(Instant agora) {
            return dados.entrySet().stream().filter(e -> !e.getValue().expiraEm().isAfter(agora)).map(Map.Entry::getKey).toList();
        }
        public long criadosDesde(Instant desde) { return criacoes.stream().filter(c -> !c.isBefore(desde)).count(); }
    }

    public final ReconhecimentoFake reconhecimento = new ReconhecimentoFake();
    public final AlunosFake alunos = new AlunosFake();
    public final PlanosFake planos = new PlanosFake();
    public final MatriculasFake matriculas = new MatriculasFake();
    public final AcessosFake acessos = new AcessosFake();
    public final CheckInsPendentesEmMemoria pendentes = new CheckInsPendentesEmMemoria();
    public final RelogioFake relogio = new RelogioFake(LocalDate.of(2026, 10, 5).atTime(8, 0)); // segunda 08:00

    public final VisitantesFake visitantes = new VisitantesFake();

    {
        alunos.aoRemover = visitantes.dados::remove; // cascata do banco
    }

    public RealizarCheckIn checkIn() {
        return new RealizarCheckIn(reconhecimento, alunos, planos, matriculas, acessos, pendentes, relogio,
                new Limiares(0.41, 0.18));
    }
}
