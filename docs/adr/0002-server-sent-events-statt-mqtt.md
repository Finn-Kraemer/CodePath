# ADR-2: Server-Sent Events statt MQTT

**Status:** Akzeptiert, umgesetzt in Phase 1

**Kontext:** Live-Updates fließen nur vom Server zum Browser (Leaderboard, Ankündigungen). MQTT verlangt Broker-Zugangsdaten im Browser.

**Entscheidung:** Ein SSE-Endpunkt in Spring MVC, geschützt durch dieselbe Session.

**Alternativen:** WebSocket (zweiseitig, hier unnötig); Polling (einfach, aber träger; bleibt als Fallback).

**Konsequenzen:** Kein Broker, keine Zugangsdaten im Frontend. Bei 90 offenen Verbindungen reichen die Standard-Threads, bei Bedarf virtuelle Threads.
