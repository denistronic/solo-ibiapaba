#!/usr/bin/env python3
"""Gera configuração local sem sobrescrever arquivo existente (Python 3)."""
from pathlib import Path
import os
import secrets


def main():
    root = Path(__file__).resolve().parent.parent
    destination = root / ".env"
    if destination.exists():
        raise SystemExit(".env já existe; preservado. Edite esse arquivo para mudar a configuração.")
    text = (root / ".env.example").read_text(encoding="utf-8")
    for key, value in {
        "POSTGRES_PASSWORD": secrets.token_hex(24),
        "JWT_SECRET": secrets.token_hex(32),
        "ADMIN_PASSWORD": secrets.token_urlsafe(18),
    }.items():
        text = text.replace(key + "=\n", key + "=" + value + "\n")
    try:
        fd = os.open(destination, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    except FileExistsError:
        raise SystemExit(".env já existe; preservado.")
    with os.fdopen(fd, "w", encoding="utf-8", newline="\n") as stream:
        stream.write(text)
    print(".env criado. Consulte ADMIN_EMAIL e ADMIN_PASSWORD nesse arquivo para entrar.")


if __name__ == "__main__":
    main()
