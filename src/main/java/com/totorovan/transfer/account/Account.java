package com.totorovan.transfer.account;

import akka.actor.AbstractLoggingActor;
import akka.actor.Props;
import com.totorovan.transfer.common.Messages.Failure;
import com.totorovan.transfer.common.Messages.Success;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

public class Account extends AbstractLoggingActor {

    private AccountInfo accountInfo;

    private Account(AccountInfo accountInfo) {
        this.accountInfo = accountInfo;
    }

    static Props props(AccountInfo accountInfo) {
        return Props.create(Account.class, () -> new Account(accountInfo));
    }

    private BigDecimal getBalance() {
        return accountInfo.getBalance();
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(Withdraw.class, withdraw -> this.withdraw(withdraw.getAmount()))
                .match(Deposit.class, deposit -> this.deposit(deposit.getAmount()))
                .match(GetAccount.class, this::onGetAccountInfo)
                .build();
    }

    private void withdraw(BigDecimal amount) {
        BigDecimal balance = getBalance();
        if (amount.compareTo(balance) > 0) {
            notifyInsufficientBalance(amount);
        } else {
            updateBalance(balance.subtract(amount), "Withdraw");
        }
    }

    private void notifyInsufficientBalance(BigDecimal amount) {
        String errorMsg = "Insufficient balance to withdraw " + amount + " from account " + accountInfo;
        log().info(errorMsg);
        sender().tell(new Failure(errorMsg), self());
    }

    private void deposit(BigDecimal amount) {
        BigDecimal balance = getBalance();
        updateBalance(balance.add(amount), "Deposit");
    }

    private void updateBalance(BigDecimal newBalance, String operation) {
        accountInfo = new AccountInfo(accountInfo.getId(), newBalance);
        log().info("{} succeeded for {}", operation, accountInfo);
        sender().tell(new Success(), self());
    }

    private void onGetAccountInfo(GetAccount getAccount) {
        sender().tell(accountInfo, self());
    }

    @Data
    static class Withdraw implements Serializable {
        private final BigDecimal amount;
    }

    @Data
    static class Deposit implements Serializable {
        private final BigDecimal amount;
    }

    @Data
    public static class GetAccount implements Serializable {
        private final long id;
    }
}
