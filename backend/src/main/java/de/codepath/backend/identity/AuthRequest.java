package de.codepath.backend.identity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param joinCode nur bei der Registrierung: Beitrittscode des Kurses
 */
public record AuthRequest(
        @NotBlank(message = "Bitte einen Benutzernamen angeben.")
        @Size(min = 3, max = 30, message = "Der Benutzername muss 3 bis 30 Zeichen haben.")
        @Pattern(regexp = "[A-Za-z0-9._-]+", message = "Der Benutzername darf nur Buchstaben, Ziffern, Punkt, Binde- und Unterstrich enthalten.")
        String username,
        @NotBlank(message = "Bitte ein Passwort angeben.")
        @Size(max = 200, message = "Das Passwort ist zu lang.")
        String password,
        String joinCode) {
}
