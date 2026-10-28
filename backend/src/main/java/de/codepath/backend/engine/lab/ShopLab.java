package de.codepath.backend.engine.lab;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * „Der Schul-Shop“: Inspektor (ausgeblendeter Artikel, data-Attribut) → Inspektor + Netzwerk
 * (deaktivierter Button, fehlerhafte Anfrage) → Konsole (Anfrage korrigiert neu senden) →
 * Web-Speicher + Konsole (Base64 dekodieren).
 */
@Component
public class ShopLab implements LabScenario {

    @Override
    public String id() {
        return "shop";
    }

    @Override
    public String title() {
        return "Der Schul-Shop";
    }

    @Override
    public List<String> tools() {
        return List.of("Inspektor", "Netzwerkanalyse", "Konsole", "Web-Speicher");
    }

    @Override
    public int parts() {
        return 3;
    }

    @Override
    public LabResponse handle(LabRequest r) {
        LabCodes codes = r.codes();
        return switch (r.path()) {
            case "" -> LabResponse.html(LabPage.page(title(), r.basePath(), "",
                    BODY.replace("{{TEIL1}}", codes.part(0))));
            case "shop.js" -> {
                String warenkorb = java.util.Base64.getEncoder().encodeToString(LabPage.json(Map.of(
                        "artikel", List.of("hoodie"), "summe", 34.9, "gutschein", "Teil 3: " + codes.part(2)))
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                yield LabResponse.script(SCRIPT.replace("{{BASIS}}", r.basePath()).replace("{{WARENKORB}}", warenkorb));
            }
            case "bestellung" -> order(r, codes);
            default -> LabResponse.notFound();
        };
    }

    private LabResponse order(LabRequest r, LabCodes codes) {
        if (!"POST".equals(r.method())) return LabResponse.json(405, LabPage.json(Map.of("fehler", "Nur POST")));
        Map<?, ?> body = LabPage.parse(r.body());
        if (body == null) return LabResponse.json(400, LabPage.json(Map.of("fehler", "Kein gültiges JSON")));
        if (!"hoodie".equals(body.get("artikel"))) {
            return LabResponse.json(400, LabPage.json(Map.of("fehler", "Ungültige Bestellung", "details", "artikel: unbekannt")));
        }
        Object menge = body.get("menge");
        if (!(menge instanceof Number n) || n.doubleValue() != Math.rint(n.doubleValue())) {
            return LabResponse.json(400, LabPage.json(Map.of("fehler", "Ungültige Bestellung",
                    "details", "menge muss eine ganze Zahl sein, kein Text")));
        }
        if (n.intValue() < 1 || n.intValue() > 5) {
            return LabResponse.json(400, LabPage.json(Map.of("fehler", "Ungültige Bestellung", "details", "menge: 1 bis 5")));
        }
        return LabResponse.json(201, LabPage.json(Map.of("bestellnummer", "2026-" + codes.extra("bestellung", 6),
                "status", "angenommen", "bestaetigung", "Teil 2: " + codes.part(1))));
    }

    private static final String BODY = """
            <h1>Schul-Merch</h1>
            <p>Offizielle Fanartikel der Schule. Lieferung in die Klasse.</p>
            <div class="karte">
              <article class="produkt" data-artikel="hoodie"><h2>Hoodie</h2><p>34,90 €</p></article>
              <article class="produkt" data-artikel="tasse"><h2>Tasse</h2><p>9,90 €</p></article>
              <article class="produkt auslauf" data-artikel="cap" data-lagercode="Teil 1: {{TEIL1}}" hidden>
                <h2>Cap (Auslaufartikel)</h2><p>nicht mehr lieferbar</p></article>
            </div>
            <div class="karte">
              <h2>Bestellen</h2>
              <label>Menge <input id="menge" type="text" value="2" size="3"></label>
              <button id="bestellen" disabled title="Wartungsarbeiten">Hoodie bestellen</button>
              <p id="meldung"></p>
            </div>
            <script src="{{BASIS}}/shop.js"></script>
            """;

    private static final String SCRIPT = """
            (() => {
              const basis = '{{BASIS}}';
              sessionStorage.setItem('warenkorb', '{{WARENKORB}}');

              document.getElementById('bestellen').addEventListener('click', async () => {
                const menge = document.getElementById('menge').value;
                const antwort = await fetch(basis + '/bestellung', {
                  method: 'POST',
                  headers: { 'Content-Type': 'application/json' },
                  body: JSON.stringify({ artikel: 'hoodie', menge })
                });
                document.getElementById('meldung').textContent = antwort.ok
                  ? 'Bestellung angenommen.'
                  : 'Bestellung fehlgeschlagen.';
              });
            })();
            """;
}
