package com.totorovan.transfer.transaction;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.testkit.javadsl.TestKit;
import com.totorovan.transfer.account.Account;
import com.totorovan.transfer.account.AccountFactory;
import com.totorovan.transfer.account.AccountInfo;
import com.totorovan.transfer.account.AccountService;
import com.totorovan.transfer.common.Messages;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import static com.totorovan.transfer.transaction.TransactionInfo.TransactionStatus.NEW;

class ConcurrentTest {

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

    @Test
    void testTransferIsThreadSafe() {
        new TestKit(system) {{
            int numThreads = 100;
            AccountInfo srcAccountInfo = new AccountInfo(1L, BigDecimal.valueOf(numThreads));
            AccountInfo targetAccountInfo = new AccountInfo(2L, BigDecimal.ZERO);
            ActorRef accountService = system.actorOf(AccountService.props(new AccountFactory()));
            ActorRef transactionService = system.actorOf((TransactionService.props(accountService, new TransactionFactory())));

            accountService.tell(srcAccountInfo, getRef());
            expectMsgClass(Messages.Success.class);

            accountService.tell(targetAccountInfo, getRef());
            expectMsgClass(Messages.Success.class);

            ExecutorService service = Executors.newFixedThreadPool(numThreads);
            final CountDownLatch latch = new CountDownLatch(1);
            AtomicLong nextId = new AtomicLong();

            for (int i = 0; i < numThreads; i++) {
                service.submit(() -> {
                    try {
                        TransactionInfo transactionInfo = new TransactionInfo(nextId.incrementAndGet(), 1L, 2L, BigDecimal.ONE, NEW);
                        latch.await();
                        transactionService.tell(transactionInfo, getRef());
                    } catch (InterruptedException ignored) {
                    }
                });
            }

            latch.countDown();
            receiveN(numThreads);

            accountService.tell(new Account.GetAccount(1L), getRef());
            expectMsg(new AccountInfo(1L, BigDecimal.ZERO));

            accountService.tell(new Account.GetAccount(2L), getRef());
            expectMsg(new AccountInfo(2L, BigDecimal.valueOf(numThreads)));
        }};
    }
}
