package com.totorovan.transfer.account;

import akka.actor.AbstractLoggingActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import com.totorovan.transfer.common.Messages.Failure;
import com.totorovan.transfer.common.Messages.Success;
import com.totorovan.transfer.transaction.TransactionInfo;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import static akka.pattern.PatternsCS.ask;

public class AccountService extends AbstractLoggingActor {

    private final AccountFactory accountFactory;
    private final Map<Long, ActorRef> accountsById = new HashMap<>();
    private final Duration timeout;

    private AccountService(AccountFactory accountFactory, Duration timeout) {
        this.accountFactory = accountFactory;
        this.timeout = timeout;
    }

    public static Props props(AccountFactory accountFactory, Duration timeout) {
        return Props.create(AccountService.class, () -> new AccountService(accountFactory, timeout));
    }

    public static Props props(AccountFactory accountFactory) {
        return AccountService.props(accountFactory, Duration.ofSeconds(1));
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(AccountInfo.class, this::onAccountInfo)
                .match(Account.GetAccount.class, this::onGetAccount)
                .match(DeleteAccount.class, this::onDeleteAccount)
                .match(TransactionInfo.class, this::onTransfer)
                .build();
    }

    private void onAccountInfo(AccountInfo accountInfo) {
        long id = accountInfo.getId();
        if (accountsById.containsKey(id)) {
            replyAccountAlreadyExists(id);
            return;
        }
        createAccount(accountInfo);
    }

    private void replyAccountAlreadyExists(long id) {
        String errorMsg = "Account " + id + " already exists";
        log().info(errorMsg);
        sender().tell(new Failure(errorMsg), self());
    }

    private void createAccount(AccountInfo accountInfo) {
        long id = accountInfo.getId();
        ActorRef actorRef = accountFactory.get(context(), accountInfo);
        accountsById.put(id, actorRef);
        log().info("Account {} created", id);
        sender().tell(new Success(), self());
    }

    private void onDeleteAccount(DeleteAccount deleteAccount) {
        long id = deleteAccount.getId();
        ActorRef account = accountsById.remove(id);
        if (account == null) {
            replyAccountNotFound(id);
            return;
        }
        context().stop(account);
        replyAccountDeleted(id);
    }

    private void replyAccountDeleted(long id) {
        log().info("Account {} deleted", id);
        sender().tell(new Success(), self());
    }

    private void onGetAccount(Account.GetAccount getAccount) {
        long id = getAccount.getId();
        ActorRef account = accountsById.get(id);
        if (account == null) {
            replyAccountNotFound(id);
            return;
        }
        forwardGetAccount(account, getAccount);
    }

    private void forwardGetAccount(ActorRef account, Account.GetAccount getAccount) {
        ActorRef replyTo = sender();
        ask(account, getAccount, timeout)
                .thenAcceptAsync(accountInfo -> replyTo.tell(accountInfo, self()));
    }

    private void onTransfer(TransactionInfo transactionInfo) {
        log().info("Processing transaction {}", transactionInfo);

        ActorRef srcAccount = tryGetAccount(transactionInfo.getSrcAccountId(), transactionInfo);
        if (srcAccount == null) return;

        ActorRef targetAccount = tryGetAccount(transactionInfo.getTargetAccountId(), transactionInfo);
        if (targetAccount == null) return;

        transfer(transactionInfo, srcAccount, targetAccount);
    }

    private ActorRef tryGetAccount(long srcAccountId, TransactionInfo transactionInfo) {
        ActorRef srcAccount = accountsById.get(srcAccountId);
        if (srcAccount == null) {
            replyTransactionWithNonExistingAccount(srcAccountId, transactionInfo);
            return null;
        }
        return srcAccount;
    }

    private void replyTransactionWithNonExistingAccount(long targetAccountId, TransactionInfo transactionInfo) {
        log().warning("Transaction {} failed", transactionInfo);
        replyAccountNotFound(targetAccountId);
    }

    private void replyAccountNotFound(long id) {
        String errorMsg = "Account " + id + " not found";
        log().warning(errorMsg);
        sender().tell(new Failure(errorMsg), self());
    }

    private void transfer(TransactionInfo transactionInfo, ActorRef srcAccount, ActorRef targetAccount) {
        ActorRef replyTo = sender();
        withdrawSrcAccount(srcAccount, transactionInfo.getAmount())
                .thenAcceptAsync(responseFromSrc -> {
                    if (responseFromSrc instanceof Failure) {
                        replyTransferFailed(transactionInfo, (Failure) responseFromSrc, replyTo);
                    } else {
                        depositTargetAccount(transactionInfo, srcAccount, targetAccount, replyTo);
                    }
                });
    }

    private CompletionStage<Object> withdrawSrcAccount(ActorRef srcAccount, BigDecimal amount) {
        return ask(srcAccount, new Account.Withdraw(amount), timeout);
    }

    private void depositTargetAccount(TransactionInfo transactionInfo, ActorRef srcAccount, ActorRef targetAccount, ActorRef replyTo) {
        ask(targetAccount, new Account.Deposit(transactionInfo.getAmount()), timeout)
                .thenAcceptAsync(depositResponse -> {
                    if (depositResponse instanceof Failure) {
                        replyTransferFailed(transactionInfo, (Failure) depositResponse, replyTo);
                        revertSrcAccountBalance(transactionInfo, srcAccount);
                    } else {
                        log().info("Transaction {} succeeded", transactionInfo.getId());
                        replyTo.tell(new Success(), self());
                    }
                });
    }

    private void replyTransferFailed(TransactionInfo transactionInfo, Failure failure, ActorRef replyTo) {
        log().warning("Transaction {} failed with reason: {}", transactionInfo.getId(), failure.getMessage());
        replyTo.tell(failure, self());
    }

    private void revertSrcAccountBalance(TransactionInfo transactionInfo, ActorRef srcAccount) {
        srcAccount.tell(new Account.Deposit(transactionInfo.getAmount()), self());
    }

    @Data
    static class DeleteAccount implements Serializable {
        private final long id;
    }
}
