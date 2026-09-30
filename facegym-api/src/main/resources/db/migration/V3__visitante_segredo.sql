-- Hash (SHA-256) do segredo entregue a quem cadastrou o visitante: só essa pessoa pode apagá-lo antes do prazo.
ALTER TABLE visitante ADD COLUMN segredo_hash text;
