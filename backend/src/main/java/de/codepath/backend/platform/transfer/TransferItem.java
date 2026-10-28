package de.codepath.backend.platform.transfer;

/**
 * Eine Zeile im Import-Bericht: was mit einem Eintrag der Datei passiert ist.
 *
 * @param area    Bereich, z. B. "Seiten"
 * @param name    Eintrag, z. B. der Slug einer Seite
 * @param message Begründung bei Fehlern oder ein Hinweis, sonst null
 */
public record TransferItem(String area, String name, Action action, String message) {

    public enum Action {
        /** Neu angelegt */
        CREATED,
        /** Gab es schon, wurde überarbeitet */
        UPDATED,
        /** Gab es schon in genau dieser Form – nichts getan */
        UNCHANGED,
        /** Nicht übernommen, siehe message */
        ERROR
    }

    public static TransferItem created(String area, String name) {
        return new TransferItem(area, name, Action.CREATED, null);
    }

    public static TransferItem updated(String area, String name) {
        return new TransferItem(area, name, Action.UPDATED, null);
    }

    public static TransferItem unchanged(String area, String name) {
        return new TransferItem(area, name, Action.UNCHANGED, null);
    }

    public static TransferItem error(String area, String name, String message) {
        return new TransferItem(area, name, Action.ERROR, message);
    }
}
