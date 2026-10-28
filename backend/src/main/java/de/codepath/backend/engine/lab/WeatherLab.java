package de.codepath.backend.engine.lab;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * „Die kaputte Wetterstation“: Konsole → Netzwerk (neue API-Version, Antwort und Header lesen) →
 * Quelltext lesen und Web-Speicher setzen (Diagnosemodus) → Konsole mit dem gefundenen Schlüssel.
 */
@Component
public class WeatherLab implements LabScenario {

    @Override
    public String id() {
        return "wetter";
    }

    @Override
    public String title() {
        return "Die kaputte Wetterstation";
    }

    @Override
    public List<String> tools() {
        return List.of("Konsole", "Netzwerkanalyse", "Web-Speicher", "Quelltext");
    }

    @Override
    public int parts() {
        return 3;
    }

    String key(LabCodes codes) {
        return codes.extra("schluessel", 8);
    }

    @Override
    public LabResponse handle(LabRequest r) {
        LabCodes codes = r.codes();
        return switch (r.path()) {
            case "" -> LabResponse.html(LabPage.page(title(), r.basePath(), "", BODY));
            case "app.js" -> LabResponse.script(SCRIPT.replace("{{BASIS}}", r.basePath()));
            case "daten" -> {
                if (!"2".equals(r.query().get("v"))) {
                    yield LabResponse.json(200, LabPage.json(Map.of("version", 1,
                            "hinweis", "Schnittstelle v1 ist abgeschaltet. Bitte Parameter v=2 verwenden.")));
                }
                yield LabResponse.json(200, LabPage.json(Map.of("version", 2, "station", "Schulhof",
                                "messwerte", Map.of("temperatur", 14.5, "wind", 12, "regen", 0.2),
                                "pruefcode", "Teil 1: " + codes.part(0))),
                        Map.of("X-Api-Schluessel", key(codes)));
            }
            case "diagnose" -> "an".equals(r.header("X-Diagnose"))
                    ? LabResponse.json(200, LabPage.json(Map.of("bericht",
                    "Sensor Temperatur: OK\nSensor Wind: OK\nSensor Regen: Kalibrierung fällig\nWartungscode: Teil 2: " + codes.part(1))))
                    : LabResponse.json(403, LabPage.json(Map.of("fehler", "Diagnose ist nicht aktiviert.")));
            case "verlauf" -> key(codes).equals(r.header("X-Api-Schluessel"))
                    ? LabResponse.json(200, LabPage.json(Map.of("tage", List.of(
                    Map.of("tag", "Montag", "max", 16, "min", 7, "notiz", "sonnig"),
                    Map.of("tag", "Dienstag", "max", 13, "min", 6, "notiz", "Teil 3: " + codes.part(2)),
                    Map.of("tag", "Mittwoch", "max", 11, "min", 4, "notiz", "Regen")))))
                    : LabResponse.json(401, LabPage.json(Map.of("fehler", "Schlüssel fehlt oder ist falsch.")));
            default -> LabResponse.notFound();
        };
    }

    private static final String BODY = """
            <h1>Wetterstation Schulhof</h1>
            <div id="fehler" class="fehler" hidden>Die Wetterdaten konnten nicht geladen werden.
              Entwickler: Details stehen in der Konsole.</div>
            <div class="karte" id="werte">
              <p>Temperatur: <b id="temperatur">–</b> °C · Wind: <b id="wind">–</b> km/h · Regen: <b id="regen">–</b> mm</p>
            </div>
            <section class="karte" id="diagnose" hidden>
              <h2>Diagnose</h2>
              <pre>lädt …</pre>
            </section>
            <script src="{{BASIS}}/app.js"></script>
            """;

    private static final String SCRIPT = """
            (() => {
              const basis = '{{BASIS}}';

              async function laden() {
                const antwort = await fetch(basis + '/daten?v=1');
                const daten = await antwort.json();
                if (!daten.messwerte) {
                  console.error('Wetterdaten unvollständig – die Schnittstelle hat etwas mitgeteilt. Schau dir die Antwort an.');
                  document.getElementById('fehler').hidden = false;
                  return;
                }
                for (const [name, wert] of Object.entries(daten.messwerte)) {
                  document.getElementById(name).textContent = wert;
                }
              }

              // Diagnosemodus nur für die Wartung
              const schalter = ['wetter', 'diagnose'].join('-');
              if (localStorage.getItem(schalter) === ['a', 'n'].join('')) {
                const bereich = document.getElementById('diagnose');
                bereich.hidden = false;
                fetch(basis + '/diagnose', { headers: { 'X-Diagnose': 'an' } })
                  .then((r) => r.json())
                  .then((d) => (bereich.querySelector('pre').textContent = d.bericht ?? d.fehler));
              }

              window.wetter = {
                hilfe() {
                  console.log('wetter.verlauf(schluessel) zeigt den Messverlauf.\\n' +
                    'Den Schlüssel schickt die aktuelle Daten-Schnittstelle bei jeder Antwort mit – aber nicht im Inhalt.');
                },
                async verlauf(schluessel) {
                  const antwort = await fetch(basis + '/verlauf', { headers: { 'X-Api-Schluessel': String(schluessel ?? '') } });
                  const daten = await antwort.json();
                  if (!antwort.ok) return console.warn(daten.fehler);
                  console.table(daten.tage);
                }
              };

              console.info('Für Entwickler: wetter.hilfe()');
              laden();
            })();
            """;
}
