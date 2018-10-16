package com.totorovan.transfer.transaction;

import akka.actor.*;
import akka.testkit.javadsl.TestKit;
import com.totorovan.transfer.common.Messages.Failure;
import com.totorovan.transfer.common.Messages.Success;
import com.totorovan.transfer.transaction.TransactionService.TransactionRolledBack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static com.totorovan.transfer.transaction.TransactionInfo.TransactionStatus.*;
import static java.math.BigDecimal.TEN;

class TransactionServiceTest {
    private static final long TR_ID = 1L;
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
    void testCreateTransactionReturnsCommittedTransactionIfTransferSucceeded() {
        new TestKit(system) {{
            TransactionInfo trInfo = new TransactionInfo(TR_ID, 1L, 2L, TEN, NEW);
            ActorRef tr1 = getTestTransaction(trInfo);
            ActorRef transactionService = getTestTransactionService(getTestAccountService(false), Collections.singletonMap(TR_ID, tr1));

            transactionService.tell(trInfo, getRef());

            expectMsg(new TransactionInfo(TR_ID, 1L, 2L, TEN, COMMITTED));
        }};
    }

    @Test
    void testCreateTransactionReturnsTransactionRolledBackIfTransferFailed() {
        new TestKit(system) {{
            TransactionInfo trInfo = new TransactionInfo(TR_ID, 1L, 2L, TEN, NEW);
            ActorRef tr1 = getTestTransaction(trInfo);
            ActorRef transactionService = getTestTransactionService(getTestAccountService(true), Collections.singletonMap(TR_ID, tr1));

            transactionService.tell(trInfo, getRef());

            expectMsg(new TransactionRolledBack(new TransactionInfo(TR_ID, 1L, 2L, TEN, ROLLEDBACK), trInfo.toString()));
        }};
    }

    private ActorRef getTestTransactionService(ActorRef accountService, Map<Long, ActorRef> transactionsById) {
        Props props = TransactionService.props(accountService, new TestTransactionFactory(transactionsById));
        return system.actorOf(props);
    }

    ActorRef getTestAccountService(boolean failOnTransfer) {
        Props props = TestAccountService.props(failOnTransfer);
        return system.actorOf(props);
    }

    static class TestAccountService extends AbstractActor {

        private boolean failOnTransfer;

        TestAccountService(boolean failOnTransfer) {
            this.failOnTransfer = failOnTransfer;
        }

        static Props props(boolean failOnTransfer) {
            return Props.create(TestAccountService.class, () -> new TestAccountService(failOnTransfer));
        }

        @Override
        public Receive createReceive() {
            return receiveBuilder()
                    .match(TransactionInfo.class, x -> {
                        if (failOnTransfer) {
                            sender().tell(new Failure(x.toString()), self());
                        } else {
                            sender().tell(new Success(), self());
                        }
                    })
                    .build();

        }
    }

    static class TestTransactionFactory extends TransactionFactory {
        private Map<Long, ActorRef> transactionsById;

        TestTransactionFactory(Map<Long, ActorRef> transactionsById) {
            this.transactionsById = transactionsById;
        }

        @Override
        ActorRef get(ActorContext context, TransactionInfo transactionInfo) {
            return transactionsById.get(transactionInfo.getId());
        }
    }
}
