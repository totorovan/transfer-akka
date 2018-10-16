package com.totorovan.transfer.account;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.javadsl.TestKit;
import com.totorovan.transfer.account.Account.Deposit;
import com.totorovan.transfer.account.Account.GetAccount;
import com.totorovan.transfer.account.Account.Withdraw;
import com.totorovan.transfer.common.Messages.Failure;
import com.totorovan.transfer.common.Messages.Success;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static java.math.BigDecimal.*;

class AccountTest {
    private static final long ACC_ID = 1L;
    private static ActorSystem system;

    @BeforeAll
    static void setup() {
        system = ActorSystem.create();
    }

    @AfterAll
    static void shutdown() {
        TestKit.shutdownActorSystem(system);
        system = null;
    }

    private static ActorRef getTestAccount(AccountInfo accountInfo) {
        Props props = Account.props(accountInfo);
        return system.actorOf(props);
    }

    @Test
    void testGetAccountReturnsExpectedAccountInfo() {
        new TestKit(system) {{
            AccountInfo expectedAccountInfo = new AccountInfo(ACC_ID, TEN);
            ActorRef account = getTestAccount(expectedAccountInfo);

            account.tell(new GetAccount(ACC_ID), getRef());

            expectMsg(expectedAccountInfo);
        }};
    }

    @Test
    void testWithdrawReturnsSuccessAndChangesAccountState() {
        new TestKit(system) {{
            AccountInfo accountInfo = new AccountInfo(ACC_ID, TEN);
            ActorRef account = getTestAccount(accountInfo);

            account.tell(new Withdraw(ONE), getRef());

            expectMsg(new Success());

            account.tell(new GetAccount(ACC_ID), getRef());
            expectMsg(new AccountInfo(ACC_ID, new BigDecimal(9)));
        }};
    }

    @Test
    void testWithdrawReturnsFailureWhenInsufficientBalance() {
        new TestKit(system) {{
            AccountInfo accountInfo = new AccountInfo(ACC_ID, ZERO);
            ActorRef account = getTestAccount(accountInfo);

            account.tell(new Withdraw(ONE), getRef());

            expectMsg(new Failure("Insufficient balance to withdraw " + ONE + " from account " + accountInfo));
        }};
    }

    @Test
    void testDepositReturnsSuccessAndChangesAccountState() {
        new TestKit(system) {{
            AccountInfo accountInfo = new AccountInfo(ACC_ID, ZERO);
            ActorRef account = getTestAccount(accountInfo);

            account.tell(new Deposit(ONE), getRef());

            expectMsg(new Success());

            account.tell(new GetAccount(ACC_ID), getRef());
            expectMsg(new AccountInfo(1, ONE));
        }};
    }
}
