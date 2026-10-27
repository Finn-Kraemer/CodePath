# ADR-3: Serverseitige Sessions statt JWT

**Status:** Vorgeschlagen

**Kontext:** Frontend und API kommen vom selben Server. Das heutige JWT lässt sich nicht widerrufen und hing an einem Secret mit Standardwert.

**Entscheidung:** Spring Session JDBC mit HttpOnly-Cookie und CSRF-Token.

**Alternativen:** JWT behalten mit Pflicht-Secret und Sperrliste (mehr Code, gleicher Nutzen).

**Konsequenzen:** Die Lehrkraft kann Sitzungen beenden; Sessions überleben Neustarts; eine Tabelle mehr in PostgreSQL.

Übergang (Phase 0): `JWT_SECRET` ist Pflicht und hat keinen Standardwert mehr.
