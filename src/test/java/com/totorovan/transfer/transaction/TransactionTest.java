package com.totorovan.transfer.transaction;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.javadsl.TestKit;
import com.totorovan.transfer.transaction.TransactionInfo.TransactionStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static com.totorovan.transfer.transaction.TransactionInfo.TransactionStatus.NEW;
import static java.math.BigDecimal.TEN;

class TransactionTest {
    private final static long TRANSACTION_ID = 1L;
    private final static long SRC_ACC_ID = 1L;
    private final static long TARGET_ACC_ID = 2L;
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

    private static ActorRef getTestTransaction(TransactionInfo transactionInfo) {
        Props props = Transaction.props(transactionInfo);
        return system.actorOf(props);
    }

    @Test
    void testGetAccountReturnsExpectedAccountInfo() {
        new TestKit(system) {{
            TransactionInfo expectedAccountInfo = new TransactionInfo(TRANSACTION_ID, SRC_ACC_ID, TARGET_ACC_ID, TEN, NEW);
            ActorRef account = getTestTransaction(expectedAccountInfo);

            account.tell(new Transaction.GetTransaction(TRANSACTION_ID), getRef());

            expectMsg(expectedAccountInfo);
        }};
    }

    @ParameterizedTest
    @EnumSource(TransactionStatus.class)
    void testChangeStatusReturnsExpectedTransactionInfo(TransactionStatus status) {
        new TestKit(system) {{
            ActorRef transaction = getTestTransaction(new TransactionInfo(TRANSACTION_ID, SRC_ACC_ID, TARGET_ACC_ID, TEN, NEW));

            transaction.tell(new Transaction.ChangeStatus(status), getRef());

            TransactionInfo expectedTransactionInfo = new TransactionInfo(TRANSACTION_ID, SRC_ACC_ID, TARGET_ACC_ID, TEN, status);

            expectMsg(expectedTransactionInfo);
        }};
    }
}
