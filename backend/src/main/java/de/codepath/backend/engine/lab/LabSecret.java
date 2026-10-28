package de.codepath.backend.engine.lab;

/** Geheimnis, aus dem die Codes im Browser-Labor abgeleitet werden. Bleibt über Neustarts gleich. */
public interface LabSecret {

    byte[] secret();
}
