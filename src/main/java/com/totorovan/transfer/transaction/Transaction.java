package com.totorovan.transfer.transaction;

import akka.actor.AbstractActor;
import akka.actor.Props;
import lombok.Data;

import java.io.Serializable;

public class Transaction extends AbstractActor {

    private TransactionInfo transactionInfo;

    private Transaction(TransactionInfo transactionInfo) {
        this.transactionInfo = transactionInfo;
    }

    static Props props(TransactionInfo transactionInfo) {
        return Props.create(Transaction.class, () -> new Transaction(transactionInfo));
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(GetTransaction.class, this::onGetTransactionInfo)
                .match(ChangeStatus.class, this::onChangeStatus)
                .build();
    }

    private void onGetTransactionInfo(GetTransaction getTransaction) {
        sender().tell(transactionInfo, self());
    }

    private void onChangeStatus(ChangeStatus changeStatus) {
        TransactionInfo.TransactionStatus status = changeStatus.status;
        transactionInfo = new TransactionInfo(transactionInfo.getId(), transactionInfo.getSrcAccountId(),
                transactionInfo.getTargetAccountId(), transactionInfo.getAmount(), status);
        sender().tell(transactionInfo, self());
    }

    @Data
    static class ChangeStatus implements Serializable {
        private final TransactionInfo.TransactionStatus status;
    }

    @Data
    static class GetTransaction implements Serializable {
        private final long id;
    }
}
