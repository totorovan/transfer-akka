package com.totorovan.transfer.transaction;

import akka.actor.AbstractLoggingActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import com.totorovan.transfer.common.Messages.Failure;
import com.totorovan.transfer.common.Messages.Success;
import lombok.Data;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static akka.pattern.PatternsCS.ask;
import static com.totorovan.transfer.transaction.TransactionInfo.TransactionStatus.COMMITTED;
import static com.totorovan.transfer.transaction.TransactionInfo.TransactionStatus.ROLLEDBACK;

public class TransactionService extends AbstractLoggingActor {

    private final Map<Long, ActorRef> transactionsById = new HashMap<>();
    private final TransactionFactory transactionFactory;
    private final ActorRef accountService;
    private final Duration timeout;

    private TransactionService(ActorRef accountService, TransactionFactory transactionFactory, Duration timeout) {
        this.accountService = accountService;
        this.transactionFactory = transactionFactory;
        this.timeout = timeout;
    }

    public static Props props(ActorRef accountService, TransactionFactory transactionFactory, Duration timeout) {
        return Props.create(TransactionService.class, () -> new TransactionService(accountService, transactionFactory, timeout));
    }

    public static Props props(ActorRef accountService, TransactionFactory transactionFactory) {
        return TransactionService.props(accountService, transactionFactory, Duration.ofSeconds(1));
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(TransactionInfo.class, this::doTransaction)
                .match(Transaction.GetTransaction.class, this::onGetTransactionInfo)
                .match(DeleteTransaction.class, this::onDeleteTransaction)
                .build();
    }

    private void doTransaction(TransactionInfo transactionInfo) {
        long transactionId = transactionInfo.getId();
        if (transactionsById.containsKey(transactionId)) {
            replyTransactionAlreadyExists(transactionId);
            return;
        }
        ActorRef transaction = transactionFactory.get(context(), transactionInfo);
        transactionsById.put(transactionId, transaction);
        doTransaction(transactionInfo, transaction);
    }

    private void replyTransactionAlreadyExists(long transactionId) {
        String errorMsg = "Transaction " + transactionId + " already been processed";
        log().warning(errorMsg);
        sender().tell(new Failure(errorMsg), sender());
    }

    private void doTransaction(TransactionInfo transactionInfo, ActorRef transaction) {
        ActorRef replyTo = sender();
        ask(accountService, transactionInfo, timeout)
                .thenAcceptAsync(transferResponse -> handleTransferResponse(transaction, transferResponse, replyTo));
    }

    private void handleTransferResponse(ActorRef transaction, Object transferResponse, ActorRef replyTo) {
        if (transferResponse instanceof Failure) {
            rollbackTransaction(transaction, replyTo, (Failure) transferResponse);
        } else {
            commitTransaction(transaction, replyTo);
        }
    }

    private void rollbackTransaction(ActorRef transaction, ActorRef replyTo, Failure response) {
        ask(transaction, new Transaction.ChangeStatus(ROLLEDBACK), timeout)
                .thenAcceptAsync(updatedTransaction -> replyTransactionRolledBack(replyTo, response, (TransactionInfo) updatedTransaction));
    }

    private void replyTransactionRolledBack(ActorRef replyTo, Failure response, TransactionInfo updatedTransaction) {
        replyTo.tell(new TransactionRolledBack(updatedTransaction, response.getMessage()), replyTo);
    }

    private void commitTransaction(ActorRef transaction, ActorRef replyTo) {
        ask(transaction, new Transaction.ChangeStatus(COMMITTED), timeout)
                .thenAcceptAsync(updatedTransaction -> replyTo.tell(updatedTransaction, replyTo));
    }

    private void onGetTransactionInfo(Transaction.GetTransaction getTransaction) {
        long id = getTransaction.getId();
        ActorRef transaction = transactionsById.get(id);
        if (transaction == null) {
            replyTransactionNotFound(id);
            return;
        }
        forwardGetTransaction(transaction, getTransaction);
    }

    private void replyTransactionNotFound(long transactionId) {
        String errorMsg = "Transaction " + transactionId + " does not exist";
        log().warning(errorMsg);
        sender().tell(new Failure(errorMsg), sender());
    }

    private void forwardGetTransaction(ActorRef transaction, Transaction.GetTransaction getTransaction) {
        ActorRef replyTo = sender();
        ask(transaction, getTransaction, timeout)
                .thenAcceptAsync(transactionInfo -> replyTo.tell(transactionInfo, self()));
    }

    private void onDeleteTransaction(DeleteTransaction deleteTransaction) {
        long transactionId = deleteTransaction.getId();
        ActorRef transaction = transactionsById.remove(transactionId);
        if (transaction == null) {
            replyTransactionNotFound(transactionId);
            return;
        }
        replyTransactionDeleted(transactionId, transaction);
    }

    private void replyTransactionDeleted(long transactionId, ActorRef transaction) {
        context().stop(transaction);
        log().info("Transaction {} deleted", transactionId);
        sender().tell(new Success(), sender());
    }

    @Data
    static class DeleteTransaction {
        private final long id;
    }

    @Data
    static class TransactionRolledBack {
        private final TransactionInfo transactionInfo;
        private final String reason;
    }
}
