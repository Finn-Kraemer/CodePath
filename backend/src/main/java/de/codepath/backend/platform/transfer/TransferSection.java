package de.codepath.backend.platform.transfer;

import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Set;

/**
 * Ein Bereich, der sich exportieren und wieder importieren lässt (Einstellungen, Startseite, Seiten,
 * Wissensdatenbank, Lernpfade, Module). Jedes Fachpaket stellt seine Bereiche als Bean bereit; der
 * zentrale Export/Import im Paket {@code transfer} sammelt sie ein, ohne die Pakete selbst zu kennen.
 */
public interface TransferSection {

    /** Name des Bereichs in der Exportdatei, z. B. "pages" */
    String key();

    /** Anzeigename, z. B. "Seiten" */
    String label();

    /** Reihenfolge beim Import: Module vor Seiten und Artikeln, die auf sie verweisen */
    int order();

    /**
     * @param only Slugs der gewünschten Einträge; leer für alle
     * @return Inhalt des Bereichs als JSON (Bilder werden über ihre Schlüssel gefunden und mitgepackt)
     */
    JsonNode export(Set<String> only);

    /** Übernimmt den Bereich; jeder Eintrag landet mit Ergebnis im Bericht, Fehler brechen nicht ab. */
    List<TransferItem> importData(JsonNode data, Long actorId);
}
