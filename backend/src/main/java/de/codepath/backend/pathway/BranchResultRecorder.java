package de.codepath.backend.pathway;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Schreibt Ergebnisse bedingter Verbindungen und Punkte-Tore fest. Die Auswertung läuft oft beim bloßen
 * Ansehen (Karte, Aufgabenseite, Empfehlung) in einer lesenden Transaktion – dann in einer eigenen,
 * sonst in der laufenden, damit keine Sperre auf die Person gegen sich selbst wartet.
 */
@Component
class BranchResultRecorder {

    private final LearningPathBranchResultRepository repository;
    private final TransactionTemplate separate;

    BranchResultRecorder(LearningPathBranchResultRepository repository, PlatformTransactionManager transactions) {
        this.repository = repository;
        this.separate = new TransactionTemplate(transactions);
        this.separate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    void record(LearningPathBranchResult result) {
        String detail = result.getDetail() != null && result.getDetail().length() > 255
                ? result.getDetail().substring(0, 255) : result.getDetail();
        Runnable insert = () -> repository.insertOnce(result.getUserId(), result.getPathId(), result.getEdgeKey(),
                result.isMatched(), result.getRule(), detail);
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && !TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            insert.run();
        } else {
            separate.executeWithoutResult(status -> insert.run());
        }
    }
}
