package com.totorovan.transfer.account;

import akka.actor.*;
import akka.testkit.javadsl.TestKit;
import com.google.common.collect.ImmutableMap;
import com.totorovan.transfer.account.Account.GetAccount;
import com.totorovan.transfer.common.Messages.Failure;
import com.totorovan.transfer.common.Messages.Success;
import com.totorovan.transfer.transaction.TransactionInfo;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;

import static com.totorovan.transfer.transaction.TransactionInfo.TransactionStatus.NEW;
import static java.math.BigDecimal.ONE;
import static java.math.BigDecimal.ZERO;

class AccountServiceTest {
    private static final long ACC_ID_1 = 1L;
    private static final long ACC_ID_2 = 2L;
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
    void testGetAccountReturnsExistingAccount() {
        new TestKit(system) {{
            AccountInfo expectedAccountInfo = new AccountInfo(ACC_ID_1, ZERO);
            ActorRef account = getTestAccount(expectedAccountInfo);

            ActorRef accountService = getTestAccountService(Collections.singletonMap(ACC_ID_1, account));

            accountService.tell(expectedAccountInfo, getRef());

            expectMsgClass(Success.class);
            accountService.tell(new GetAccount(ACC_ID_1), getRef());

            expectMsg(expectedAccountInfo);
        }};
    }

    @Test
    void testGetAccountReturnsFailureIfAccountNotExisting() {
        new TestKit(system) {{
            ActorRef accountService = getTestAccountService(Collections.emptyMap());

            accountService.tell(new GetAccount(ACC_ID_1), getRef());

            expectMsg(new Failure("Account " + ACC_ID_1 + " not found"));
        }};
    }

    @Test
    void testCreateAccountReturnsSuccessIfAccountDoesNotExist() {
        new TestKit(system) {{
            ActorRef accountService = getTestAccountService(Collections.emptyMap());

            AccountInfo expectedAccountInfo = new AccountInfo(ACC_ID_1, ZERO);
            accountService.tell(expectedAccountInfo, getRef());

            expectMsgClass(Success.class);
        }};
    }

    @Test
    void testCreateAccountReturnsFailureIfAccountExists() {
        new TestKit(system) {{
            AccountInfo expectedAccountInfo = new AccountInfo(ACC_ID_1, ZERO);
            ActorRef account = getTestAccount(expectedAccountInfo);

            ActorRef accountService = getTestAccountService(Collections.singletonMap(ACC_ID_1, account));
            accountService.tell(expectedAccountInfo, getRef());
            expectMsgClass(Success.class);

            accountService.tell(expectedAccountInfo, getRef());

            expectMsg(new Failure("Account " + ACC_ID_1 + " already exists"));
        }};
    }

    @Test
    void testDeleteAccountReturnsSuccessIfAccountExists() {
        new TestKit(system) {{
            AccountInfo expectedAccountInfo = new AccountInfo(ACC_ID_1, ZERO);
            ActorRef account = getTestAccount(expectedAccountInfo);

            ActorRef accountService = getTestAccountService(Collections.singletonMap(ACC_ID_1, account));
            accountService.tell(expectedAccountInfo, getRef());
            expectMsgClass(Success.class);

            accountService.tell(new AccountService.DeleteAccount(ACC_ID_1), getRef());

            expectMsgClass(Success.class);
        }};
    }

    @Test
    void testDeleteAccountReturnsFailureIfAccountNotFound() {
        new TestKit(system) {{
            ActorRef accountService = getTestAccountService(Collections.emptyMap());

            accountService.tell(new AccountService.DeleteAccount(ACC_ID_1), getRef());

            expectMsg(new Failure("Account " + ACC_ID_1 + " not found"));
        }};
    }

    @Test
    void testTransferReturnsSuccessIfAccountsExistAndSufficientBalance() {
        new TestKit(system) {{
            ActorRef accountService = prepareAccountServiceForTransfer(this, new AccountInfo(ACC_ID_1, ONE), new AccountInfo(ACC_ID_2, ONE));

            accountService.tell(new TransactionInfo(1L, ACC_ID_1, ACC_ID_2, ONE, NEW), getRef());

            expectMsgClass(Success.class);

            accountService.tell(new GetAccount(ACC_ID_1), getRef());
            expectMsg(new AccountInfo(ACC_ID_1, ZERO));

            accountService.tell(new GetAccount(ACC_ID_2), getRef());
            expectMsg(new AccountInfo(ACC_ID_2, BigDecimal.valueOf(2L)));
        }};
    }

    @Test
    void testTransferReturnsFailureIfAccountsExistButInsufficientBalance() {
        new TestKit(system) {{
            AccountInfo accountInfo1 = new AccountInfo(ACC_ID_1, ZERO);
            ActorRef accountService = prepareAccountServiceForTransfer(this, accountInfo1, new AccountInfo(ACC_ID_2, ONE));

            accountService.tell(new TransactionInfo(1L, ACC_ID_1, ACC_ID_2, ONE, NEW), getRef());

            expectMsg(new Failure("Insufficient balance to withdraw 1 from account " + accountInfo1));
        }};
    }

    @Test
    void testTransferReturnsFailureIfAccountsOneOfAccountsDoesNotExist() {
        new TestKit(system) {{
            ActorRef accountService = prepareAccountServiceForTransfer(this, null, new AccountInfo(ACC_ID_2, ONE));

            accountService.tell(new TransactionInfo(1L, ACC_ID_1, ACC_ID_2, ONE, NEW), getRef());

            expectMsg(new Failure("Account 1 not found"));

            accountService = prepareAccountServiceForTransfer(this, new AccountInfo(ACC_ID_1, ONE), null);

            accountService.tell(new TransactionInfo(1L, ACC_ID_1, ACC_ID_2, ONE, NEW), getRef());

            expectMsg(new Failure("Account 2 not found"));
        }};
    }

    @Test
    void testTransferReturnsFailureIfTargetAccountFailsOnDeposit() {
        new TestKit(system) {{
            AccountInfo acc1 = new AccountInfo(ACC_ID_1, ONE);
            AccountInfo acc2 = new AccountInfo(ACC_ID_2, ONE);
            ActorRef account1 = getTestAccount(acc1);
            ActorRef account2 = system.actorOf(OnDepositFailureAccount.props());
            ActorRef accountService = getTestAccountService(ImmutableMap.<Long, ActorRef>builder().put(ACC_ID_1, account1).put(ACC_ID_2, account2).build());

            accountService.tell(acc1, getRef());
            expectMsgClass(Success.class);
            accountService.tell(acc2, getRef());
            expectMsgClass(Success.class);


            accountService.tell(new TransactionInfo(1L, ACC_ID_1, ACC_ID_2, ONE, NEW), getRef());

            expectMsgClass(Failure.class);

            accountService.tell(new GetAccount(acc1.getId()), getRef());
            expectMsg(acc1);
        }};
    }

    private ActorRef prepareAccountServiceForTransfer(TestKit testKit, AccountInfo acc1, AccountInfo acc2) {
        ActorRef account1 = getTestAccount(acc1);
        ActorRef account2 = getTestAccount(acc2);

        ActorRef accountService = getTestAccountService(ImmutableMap.<Long, ActorRef>builder().put(ACC_ID_1, account1).put(ACC_ID_2, account2).build());

        if (acc1 != null) {
            accountService.tell(acc1, testKit.getRef());
            testKit.expectMsgClass(Success.class);
        }

        if (acc2 != null) {
            accountService.tell(acc2, testKit.getRef());
            testKit.expectMsgClass(Success.class);
        }

        return accountService;
    }

    private ActorRef getTestAccountService(Map<Long, ActorRef> accountsById) {
        Props props = AccountService.props(new TestAccountFactory(accountsById));
        return system.actorOf(props);
    }

    static class OnDepositFailureAccount extends AbstractActor {

        static Props props() {
            return Props.create(OnDepositFailureAccount.class, OnDepositFailureAccount::new);
        }

        @Override
        public Receive createReceive() {
            return receiveBuilder()
                    .match(Account.Deposit.class, deposit -> sender().tell(new Failure("Always fail on that"), self()))
                    .build();
        }
    }

    static class TestAccountFactory extends AccountFactory {
        private Map<Long, ActorRef> accountsById;

        TestAccountFactory(Map<Long, ActorRef> accountsById) {
            this.accountsById = accountsById;
        }

        @Override
        ActorRef get(ActorContext context, AccountInfo accountInfo) {
            return accountsById.get(accountInfo.getId());
        }
    }
}
