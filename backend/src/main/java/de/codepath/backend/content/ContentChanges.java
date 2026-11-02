package de.codepath.backend.content;

import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Stand der Inhalte (Module, Aufgaben, Versionen) für den Cache in {@link ContentService}.
 * Als JPA-Listener an den Entitäten: Jede Änderung zählt hoch – beim Schreiben und noch einmal nach
 * Ende der Transaktion, damit ein Cache, der parallel noch den alten Stand gelesen hat, sicher verfällt.
 * Änderungen an Inhalten per SQL an JPA vorbei müssen {@link #changed()} selbst aufrufen.
 */
public class ContentChanges {

    private static final AtomicLong GENERATION = new AtomicLong();
    // Markiert eine Transaktion, die selbst Inhalte geändert hat: Nur sie sieht den neuen Stand schon.
    // Als Transaktions-Ressource, damit verschachtelte Transaktionen (REQUIRES_NEW) eine eigene Markierung haben.
    private static final Object CHANGED_IN_TRANSACTION = new Object();

    static long generation() {
        return GENERATION.get();
    }

    /** Hat die laufende Transaktion Inhalte geändert, die noch nicht bestätigt sind? */
    static boolean changedInCurrentTransaction() {
        return TransactionSynchronizationManager.hasResource(CHANGED_IN_TRANSACTION);
    }

    static void changed() {
        GENERATION.incrementAndGet();
        if (TransactionSynchronizationManager.isSynchronizationActive() && !changedInCurrentTransaction()) {
            TransactionSynchronizationManager.bindResource(CHANGED_IN_TRANSACTION, Boolean.TRUE);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    TransactionSynchronizationManager.unbindResourceIfPossible(CHANGED_IN_TRANSACTION);
                    GENERATION.incrementAndGet();
                }
            });
        }
    }

    @PostPersist
    @PostUpdate
    @PostRemove
    void onChange(Object entity) {
        changed();
    }
}
