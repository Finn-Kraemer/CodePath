package de.codepath.backend.platform.events;

import java.util.Collection;
import java.util.Set;

/**
 * Wer ein Ranglisten-Update eines Kurses sehen muss. Umgesetzt im identity-Paket; platform kennt
 * Kurse und Mitgliedschaften nicht. Fehlt die Umsetzung oder schlägt sie fehl, geht das Update an alle.
 */
public interface LeaderboardAudience {

    /** Benutzernamen aller Mitglieder dieser Kurse. */
    Set<String> members(Collection<Long> courseIds);
}
