package lk.hms.modules.pharmacy.service;

import lk.hms.common.security.CurrentUser;
import lk.hms.modules.pharmacy.entity.MedicineBatch;
import lk.hms.modules.pharmacy.entity.StockTransaction;
import lk.hms.modules.pharmacy.repository.StockTransactionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only way stock changes: updates the batch and appends the matching ledger row in the same
 * transaction, so quantity_on_hand always equals the sum of the batch's movements.
 * Callers hold the batch row lock.
 */
@Component
public class StockLedger {

    private final StockTransactionRepository transactions;

    public StockLedger(StockTransactionRepository transactions) {
        this.transactions = transactions;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void move(MedicineBatch batch, String type, int signedQuantity, String referenceType, Long referenceId, String note) {
        int balance = batch.getQuantityOnHand() + signedQuantity;
        if (balance < 0) {
            throw new IllegalStateException("Stock movement would make batch " + batch.getBatchNo() + " negative");
        }
        batch.setQuantityOnHand(balance);
        StockTransaction t = new StockTransaction();
        t.setBatchId(batch.getId());
        t.setMedicineId(batch.getMedicineId());
        t.setType(type);
        t.setQuantity(signedQuantity);
        t.setBalanceAfter(balance);
        t.setReferenceType(referenceType);
        t.setReferenceId(referenceId);
        t.setNote(note);
        t.setPerformedBy(CurrentUser.get().map(p -> p.getId()).orElse(null));
        transactions.save(t);
    }
}
