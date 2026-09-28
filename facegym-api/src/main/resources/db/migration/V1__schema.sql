CREATE TABLE aluno (
    id                           uuid PRIMARY KEY,
    nome                         varchar(120) NOT NULL,
    cpf                          char(11)     NOT NULL UNIQUE,
    email                        varchar(160),
    bloqueado                    boolean      NOT NULL DEFAULT false,
    motivo_bloqueio              varchar(200),
    consentimento_biometrico_em  timestamptz
);

CREATE TABLE plano (
    id              uuid PRIMARY KEY,
    nome            varchar(80)   NOT NULL,
    preco           numeric(10,2) NOT NULL,
    dias_semana     varchar(80)   NOT NULL,
    hora_inicio     time          NOT NULL,
    hora_fim        time          NOT NULL,
    acessos_semana  integer CHECK (acessos_semana > 0)
);

CREATE TABLE matricula (
    id          uuid PRIMARY KEY,
    aluno_id    uuid NOT NULL REFERENCES aluno(id),
    plano_id    uuid NOT NULL REFERENCES plano(id),
    inicio      date NOT NULL,
    vencimento  date NOT NULL,
    CHECK (vencimento >= inicio)
);
CREATE INDEX matricula_aluno_idx ON matricula (aluno_id, vencimento);

CREATE TABLE acesso (
    id         uuid PRIMARY KEY,
    data_hora  timestamptz NOT NULL,
    aluno_id   uuid REFERENCES aluno(id),
    resultado  varchar(10) NOT NULL,
    motivo     varchar(200),
    meio       varchar(10) NOT NULL,
    score      double precision
);
CREATE INDEX acesso_aluno_idx ON acesso (aluno_id, data_hora);
CREATE INDEX acesso_data_idx ON acesso (data_hora DESC);

CREATE TABLE admin (
    id          uuid PRIMARY KEY,
    email       varchar(160) NOT NULL UNIQUE,
    senha_hash  varchar(100) NOT NULL
);
