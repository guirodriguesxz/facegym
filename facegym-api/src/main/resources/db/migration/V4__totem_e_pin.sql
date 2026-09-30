-- Totens físicos: o token é entregue uma vez ao admin; aqui só o SHA-256.
CREATE TABLE totem (
    id          uuid PRIMARY KEY,
    nome        varchar(80) NOT NULL,
    token_hash  char(64)    NOT NULL UNIQUE,
    criado_em   timestamptz NOT NULL
);

-- PIN do aluno (BCrypt), segundo fator do check-in só por CPF.
CREATE TABLE aluno_pin (
    aluno_id  uuid PRIMARY KEY REFERENCES aluno(id) ON DELETE CASCADE,
    pin_hash  varchar(100) NOT NULL
);
