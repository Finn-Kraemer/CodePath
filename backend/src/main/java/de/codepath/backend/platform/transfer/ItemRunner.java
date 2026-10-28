package de.codepath.backend.platform.transfer;

import de.codepath.backend.platform.ApiException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;

import java.util.function.Supplier;

/**
 * Führt den Import eines einzelnen Eintrags in einer eigenen Transaktion aus: Ein fehlerhafter Eintrag
 * wird zurückgerollt und als Fehler gemeldet, die übrigen bleiben erhalten.
 */
@Component
public class ItemRunner {

    private final TransactionTemplate transaction;

    public ItemRunner(PlatformTransactionManager transactionManager) {
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public TransferItem run(String area, String name, Supplier<TransferItem.Action> work) {
        try {
            TransferItem.Action action = transaction.execute(status -> work.get());
            return new TransferItem(area, name, action, null);
        } catch (JacksonException e) {
            return TransferItem.error(area, name, "Ungültiges Format: " + e.getOriginalMessage());
        } catch (ApiException | IllegalArgumentException e) {
            return TransferItem.error(area, name, e.getMessage());
        } catch (RuntimeException e) {
            return TransferItem.error(area, name, "Unerwarteter Fehler: " + e.getClass().getSimpleName());
        }
    }
}
