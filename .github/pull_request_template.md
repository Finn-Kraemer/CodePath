<!-- Ablauf und Prüfungen: siehe CONTRIBUTING.md -->

## Worum geht es?

<!-- Was ändert dieser PR und warum? Verweis auf ein Issue mit "Closes #…" -->

## Wie wurde es geprüft?

- [ ] `./mvnw verify` im Backend
- [ ] `pnpm lint && pnpm check && pnpm test` im Frontend
- [ ] `python content/validate.py` (bei geänderten Inhalten)
- [ ] Im Browser ausprobiert

## Checkliste

- [ ] `CHANGELOG.md` unter **Unveröffentlicht** ergänzt (bei sichtbaren Änderungen)
- [ ] Bei API-Änderungen: `openapi.json` und `schema.d.ts` neu erzeugt und eingecheckt
- [ ] Bei neuen Migrationen: Hinweis unter **Update** im Änderungsprotokoll
- [ ] Dokumentation in `docs/` angepasst, wo nötig
