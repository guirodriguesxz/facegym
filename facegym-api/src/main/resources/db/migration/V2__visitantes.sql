-- facegym-api/src/main/resources/db/migration/V2__visitantes.sql
-- Apagar um aluno leva junto matrículas, acessos e o registro de visitante.
ALTER TABLE matricula DROP CONSTRAINT matricula_aluno_id_fkey,
    ADD CONSTRAINT matricula_aluno_id_fkey FOREIGN KEY (aluno_id) REFERENCES aluno(id) ON DELETE CASCADE;
ALTER TABLE acesso DROP CONSTRAINT acesso_aluno_id_fkey,
    ADD CONSTRAINT acesso_aluno_id_fkey FOREIGN KEY (aluno_id) REFERENCES aluno(id) ON DELETE CASCADE;

CREATE TABLE visitante (
    aluno_id   uuid PRIMARY KEY REFERENCES aluno(id) ON DELETE CASCADE,
    criado_em  timestamptz NOT NULL,
    expira_em  timestamptz NOT NULL
);
CREATE INDEX visitante_expira_idx ON visitante (expira_em);

-- Só instantes de criação, sem nenhum dado pessoal: sustenta o limite por hora
-- mesmo depois que o visitante é apagado.
CREATE TABLE visitante_log (criado_em timestamptz NOT NULL);
CREATE INDEX visitante_log_idx ON visitante_log (criado_em);

INSERT INTO plano (id, nome, preco, dias_semana, hora_inicio, hora_fim, acessos_semana)
VALUES ('00000000-0000-4000-8000-000000000001', 'Visitante', 0,
        'MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY', '00:00', '23:59', NULL);
