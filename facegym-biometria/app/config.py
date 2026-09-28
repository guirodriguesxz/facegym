import os
from dataclasses import dataclass

@dataclass(frozen=True)
class Settings:
    database_url: str
    internal_key: str

    @staticmethod
    def from_env() -> "Settings":
        key = os.environ.get("INTERNAL_KEY", "")
        if len(key) < 16:
            raise RuntimeError("INTERNAL_KEY precisa ter pelo menos 16 caracteres")
        return Settings(
            database_url=os.environ.get("DATABASE_URL", "postgresql://biometria:biometria@localhost:5433/biometria"),
            internal_key=key,
        )
