"""CodePath Code-Runner (ADR-4).

Führt eingereichten Python-Code in einer bubblewrap-Sandbox aus: ohne Netzwerk, ohne Zugriff auf
das Dateisystem außer /usr (nur lesend) und einem leeren /tmp, mit Grenzen für CPU-Zeit,
Speicher, Prozesse, Dateigröße und Ausgabe. Jeder parallele Lauf bekommt eine eigene Benutzer-ID,
damit sich Abgaben nicht gegenseitig die Prozess-Grenze wegnehmen.

Nur die App ruft den Dienst auf, über ein internes Netz und mit Bearer-Token:
  POST /run     {"code": "...", "stdin": "..."} -> {"status": "OK|ERROR|TIMEOUT", "stdout", "stderr", "durationMs"}
  GET  /health  Selbsttest der Sandbox, 200 nur wenn sie greift

Absichtlich nur Standardbibliothek, damit das Image klein und leicht zu prüfen bleibt.
"""
import hmac
import json
import os
import queue
import resource
import shutil
import signal
import subprocess
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

TOKEN = os.environ.get("RUNNER_TOKEN", "")
PORT = int(os.environ.get("RUNNER_PORT", "8000"))
PARALLEL = int(os.environ.get("RUNNER_PARALLEL", "4"))
PYTHON = os.environ.get("RUNNER_PYTHON") or os.path.realpath(sys.executable)
BWRAP = shutil.which("bwrap") or "/usr/bin/bwrap"

CPU_SECONDS = 2
WALL_SECONDS = 5
MEMORY_BYTES = 256 * 1024 * 1024
FILE_BYTES = 1024 * 1024
MAX_PROCESSES = 32
OUTPUT_BYTES = 64 * 1024
MAX_BODY_BYTES = 64 * 1024
QUEUE_SECONDS = 10
BASE_UID = 61000

# Freie Plätze; jeder Platz hat eine eigene Benutzer-ID für die Sandbox
slots: "queue.Queue[int]" = queue.Queue()
for slot in range(PARALLEL):
    slots.put(slot)


def sandbox_command(code):
    return [
        BWRAP, "--unshare-all", "--die-with-parent", "--new-session", "--clearenv",
        "--ro-bind", "/usr", "/usr",
        "--symlink", "usr/lib", "/lib", "--symlink", "usr/lib64", "/lib64", "--symlink", "usr/bin", "/bin",
        # Kein /proc: Python braucht es nicht, und in Docker darf es ohnehin nicht neu eingehängt werden
        "--dev", "/dev", "--tmpfs", "/tmp", "--chdir", "/tmp",
        "--setenv", "PATH", "/usr/local/bin:/usr/bin", "--setenv", "HOME", "/tmp",
        # Das offizielle Python-Image findet libpython sonst nur über /etc/ld.so.cache
        "--setenv", "LD_LIBRARY_PATH", "/usr/local/lib",
        "--setenv", "PYTHONDONTWRITEBYTECODE", "1", "--setenv", "PYTHONIOENCODING", "utf-8",
        PYTHON, "-I", "-S", "-c", code,
    ]


def limits(uid):
    def apply():
        resource.setrlimit(resource.RLIMIT_CPU, (CPU_SECONDS, CPU_SECONDS))
        resource.setrlimit(resource.RLIMIT_AS, (MEMORY_BYTES, MEMORY_BYTES))
        resource.setrlimit(resource.RLIMIT_FSIZE, (FILE_BYTES, FILE_BYTES))
        resource.setrlimit(resource.RLIMIT_NOFILE, (64, 64))
        resource.setrlimit(resource.RLIMIT_NPROC, (MAX_PROCESSES, MAX_PROCESSES))
        resource.setrlimit(resource.RLIMIT_CORE, (0, 0))
        os.setsid()
        if os.getuid() == 0:
            # Ohne eigene ID würde RLIMIT_NPROC für root nicht greifen
            os.setgroups([])
            os.setgid(uid)
            os.setuid(uid)
    return apply


class SandboxError(Exception):
    pass


def execute(code, stdin, uid, wall_seconds=WALL_SECONDS):
    start = time.monotonic()
    process = subprocess.Popen(sandbox_command(code), stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                               stderr=subprocess.PIPE, preexec_fn=limits(uid), close_fds=True)
    output = {"stdout": bytearray(), "stderr": bytearray()}
    flooded = threading.Event()

    def read(stream, key):
        while True:
            chunk = stream.read(4096)
            if not chunk:
                return
            buffer = output[key]
            buffer.extend(chunk[:max(0, OUTPUT_BYTES - len(buffer))])
            if len(buffer) >= OUTPUT_BYTES:
                flooded.set()
                kill(process)
                return

    readers = [threading.Thread(target=read, args=(process.stdout, "stdout"), daemon=True),
               threading.Thread(target=read, args=(process.stderr, "stderr"), daemon=True)]
    for reader in readers:
        reader.start()
    try:
        process.stdin.write(stdin.encode("utf-8"))
        process.stdin.close()
    except (BrokenPipeError, OSError):
        pass

    timed_out = False
    try:
        process.wait(timeout=wall_seconds)
    except subprocess.TimeoutExpired:
        timed_out = True
        kill(process)
        try:
            process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            raise SandboxError("Sandbox-Prozess lässt sich nicht beenden – fehlt CAP_KILL?")
    for reader in readers:
        reader.join(timeout=1)
    process.stdout.close()
    process.stderr.close()

    stdout = output["stdout"].decode("utf-8", errors="replace")
    stderr = output["stderr"].decode("utf-8", errors="replace")
    # 137 = SIGKILL durch das CPU-Limit
    if flooded.is_set():
        status, stderr = "ERROR", "OutputError: Die Ausgabe ist zu lang (mehr als 64 KB)."
    elif timed_out or process.returncode in (137, -signal.SIGKILL, 152, -signal.SIGXCPU):
        status = "TIMEOUT"
    elif process.returncode == 0:
        status = "OK"
    else:
        status = "ERROR"
    return {"status": status, "stdout": stdout, "stderr": stderr[-4000:],
            "durationMs": int((time.monotonic() - start) * 1000)}


def kill(process):
    try:
        os.killpg(process.pid, signal.SIGKILL)
    except ProcessLookupError:
        pass
    except PermissionError:
        print("Keine Berechtigung, den Sandbox-Prozess zu beenden (CAP_KILL fehlt)", file=sys.stderr, flush=True)


def run(code, stdin=""):
    try:
        slot = slots.get(timeout=QUEUE_SECONDS)
    except queue.Empty:
        return None
    try:
        return execute(code, stdin, BASE_UID + slot)
    finally:
        slots.put(slot)


# Der Selbsttest muss rechnen können und darf weder Konfiguration noch Netz erreichen
HEALTH_CODE = """
import os, socket
print(6 * 7, input())
print(os.path.exists('/etc/passwd'), os.path.exists('/app'))
try:
    socket.create_connection(('1.1.1.1', 53), timeout=1)
    print('net')
except OSError:
    print('no-net')
"""
HEALTH_EXPECTED = "42 ok\nFalse False\nno-net\n"
health = {"checked": None, "ok": False}


def healthy():
    # Erster Aufruf prüft immer – monotonic() kann kurz nach dem Booten kleiner als 60 sein
    if health["checked"] is None or time.monotonic() - health["checked"] > 60:
        result = run(HEALTH_CODE, "ok\n")
        health["ok"] = bool(result) and result["status"] == "OK" and result["stdout"] == HEALTH_EXPECTED
        health["checked"] = time.monotonic()
        if not health["ok"]:
            print(f"Selbsttest fehlgeschlagen: {result}", file=sys.stderr, flush=True)
    return health["ok"]


class Handler(BaseHTTPRequestHandler):
    server_version = "codepath-runner"
    sys_version = ""

    def do_GET(self):
        if self.path != "/health":
            return self.reply(404, {"error": "not found"})
        ok = healthy()
        self.reply(200 if ok else 503, {"status": "UP" if ok else "DOWN"})

    def do_POST(self):
        if self.path != "/run":
            return self.reply(404, {"error": "not found"})
        if not hmac.compare_digest(self.headers.get("Authorization", ""), f"Bearer {TOKEN}"):
            return self.reply(401, {"error": "unauthorized"})
        length = int(self.headers.get("Content-Length") or 0)
        if length <= 0 or length > MAX_BODY_BYTES:
            return self.reply(413, {"error": "body too large"})
        try:
            body = json.loads(self.rfile.read(length))
            code, stdin = body["code"], body.get("stdin", "") or ""
            if not isinstance(code, str) or not isinstance(stdin, str):
                raise ValueError
        except (ValueError, KeyError, TypeError):
            return self.reply(400, {"error": "bad request"})
        result = run(code, stdin)
        if result is None:
            return self.reply(503, {"error": "busy"})
        self.reply(200, result)

    def reply(self, status, payload):
        data = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, format, *args):
        # Keine Abgaben ins Log, nur Statuszeilen
        if os.environ.get("RUNNER_ACCESS_LOG"):
            super().log_message(format, *args)


def main():
    if len(TOKEN) < 16:
        sys.exit("RUNNER_TOKEN fehlt oder ist kürzer als 16 Zeichen.")
    if not healthy():
        sys.exit("Die Sandbox funktioniert nicht (siehe docs/phase-4.md: user namespaces, seccomp).")
    try:
        if execute("import time; time.sleep(30)", "", BASE_UID, wall_seconds=1)["status"] != "TIMEOUT":
            raise SandboxError("Zeitlimit greift nicht")
    except SandboxError as e:
        sys.exit(f"Die Sandbox funktioniert nicht: {e}")
    server = ThreadingHTTPServer(("0.0.0.0", PORT), Handler)
    server.daemon_threads = True
    print(f"Code-Runner bereit auf Port {PORT} ({PARALLEL} parallel, Python {PYTHON})", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
