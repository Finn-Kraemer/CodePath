# CodePath Code-Runner: Python-Abgaben in bubblewrap ausführen (ADR-4, docs/phase-4.md)
FROM python:3.13-slim
RUN apt-get update \
    && apt-get install -y --no-install-recommends bubblewrap \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY runner.py test_runner.py ./
# Läuft als root im Container, aber ohne Capabilities außer SETUID/SETGID (siehe docker-compose.yml):
# jeder Lauf wechselt vor dem Start der Sandbox auf eine eigene, unprivilegierte Benutzer-ID.
EXPOSE 8000
HEALTHCHECK --interval=30s --timeout=10s CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8000/health')"
CMD ["python", "-I", "/app/runner.py"]
