package de.codepath.backend.engine.lab;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * „Das Escape-Zimmer“ (Abschlusstest): vier Schlösser, eines je Werkzeug. Inspektor (Text nur im CSS),
 * Netzwerk (Inhalt einer Fehlerantwort), Web-Speicher (IndexedDB), Konsole (Tür mit den ersten drei
 * Teilen öffnen – der vierte Teil kommt erst dann vom Server).
 */
@Component
public class EscapeRoomLab implements LabScenario {

    @Override
    public String id() {
        return "escape";
    }

    @Override
    public String title() {
        return "Das Escape-Zimmer";
    }

    @Override
    public List<String> tools() {
        return List.of("Inspektor", "Netzwerkanalyse", "Web-Speicher", "Konsole");
    }

    @Override
    public int parts() {
        return 4;
    }

    @Override
    public LabResponse handle(LabRequest r) {
        LabCodes codes = r.codes();
        return switch (r.path()) {
            case "" -> LabResponse.html(LabPage.page(title(), r.basePath(),
                    "<link rel=\"stylesheet\" href=\"" + r.basePath() + "/zimmer.css\">", BODY));
            case "zimmer.css" -> new LabResponse(200, "text/css;charset=UTF-8",
                    CSS.replace("{{A}}", codes.part(0).substring(0, 2)).replace("{{B}}", codes.part(0).substring(2)), Map.of());
            case "zimmer.js" -> LabResponse.script(SCRIPT.replace("{{BASIS}}", r.basePath()));
            case "schluessel" -> LabResponse.json(404, LabPage.json(Map.of("fehler", "Schlüssel nicht gefunden",
                    "notiz", "Wer diese Zeile liest, hat in die Antwort geschaut. Teil 2: " + codes.part(1))));
            case "truhe" -> LabResponse.json(200, LabPage.json(Map.of("verschluesselt", true,
                    "inhalt", new StringBuilder("Teil 3: " + codes.part(2)).reverse().toString())));
            case "tuer" -> door(r, codes);
            default -> LabResponse.notFound();
        };
    }

    private LabResponse door(LabRequest r, LabCodes codes) {
        if (!"POST".equals(r.method())) return LabResponse.json(405, LabPage.json(Map.of("fehler", "Nur POST")));
        Map<?, ?> body = LabPage.parse(r.body());
        Object teile = body == null ? null : body.get("teile");
        if (!(teile instanceof List<?> list) || list.size() != 3) {
            return LabResponse.json(400, LabPage.json(Map.of("fehler", "Die Tür braucht genau drei Teile.")));
        }
        int right = 0;
        for (int i = 0; i < 3; i++) {
            if (codes.part(i).equals(String.valueOf(list.get(i)).trim().toUpperCase(java.util.Locale.ROOT))) right++;
        }
        if (right < 3) {
            return LabResponse.json(403, LabPage.json(Map.of("fehler", "Die Tür bleibt zu.", "richtig", right)));
        }
        return LabResponse.json(200, LabPage.json(Map.of("tuer", "offen", "teil4", codes.part(3))));
    }

    private static final String BODY = """
            <h1>Das Escape-Zimmer</h1>
            <div class="karte">
              <p>Du bist eingeschlossen. Die Tür hat vier Schlösser. An der Wand hängt ein Zettel:</p>
              <blockquote>„Das Erste siehst du nicht – es steht im Stil der Wand.<br>
              Das Zweite ging verloren – doch auch ein Fehler kann etwas erzählen.<br>
              Das Dritte liegt in der Truhe, gespeichert im Browser, aber verdreht.<br>
              Das Vierte gibt dir die Tür selbst – wenn du ihr die ersten drei nennst.“</blockquote>
              <p class="wand">Die Wand ist leer.</p>
            </div>
            <div class="karte">
              <p id="schluessel">Suche Schlüssel …</p>
              <p id="truhe">Die Truhe wird befüllt …</p>
              <p id="tuer">Die Tür hat ein Bedienfeld für Techniker.</p>
            </div>
            <script src="{{BASIS}}/zimmer.js"></script>
            """;

    private static final String CSS = """
            .wand { position: relative; }
            .wand::after {
              content: "Teil 1: {{A}}" "{{B}}";
              color: transparent;
              font-size: 0;
            }
            """;

    private static final String SCRIPT = """
            (() => {
              const basis = '{{BASIS}}';

              fetch(basis + '/schluessel').then((r) => {
                document.getElementById('schluessel').textContent = r.ok ? 'Schlüssel gefunden.' : 'Schlüssel nicht gefunden.';
              });

              fetch(basis + '/truhe').then((r) => r.json()).then((daten) => {
                const anfrage = indexedDB.open('escape-zimmer', 1);
                anfrage.onupgradeneeded = () => anfrage.result.createObjectStore('truhe', { keyPath: 'fach' });
                anfrage.onsuccess = () => {
                  const tx = anfrage.result.transaction('truhe', 'readwrite');
                  tx.objectStore('truhe').put({ fach: 3, inhalt: daten.inhalt });
                  tx.oncomplete = () => (document.getElementById('truhe').textContent = 'Die Truhe ist verschlossen.');
                };
              });

              window.zimmer = {
                hilfe() {
                  console.log('Techniker-Zugang: zimmer.oeffnen(teil1, teil2, teil3)');
                },
                async oeffnen(...teile) {
                  const antwort = await fetch(basis + '/tuer', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ teile })
                  });
                  const daten = await antwort.json();
                  if (!antwort.ok) {
                    console.warn(daten.fehler + (daten.richtig !== undefined ? ' Richtige Teile: ' + daten.richtig : ''));
                    return;
                  }
                  document.getElementById('tuer').textContent = 'Die Tür ist offen! Teil 4: ' + daten.teil4;
                  console.log('Geschafft. Teil 4: ' + daten.teil4);
                }
              };

              console.info('Techniker: zimmer.hilfe()');
            })();
            """;
}
