package com.totorovan.transfer;

import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.MediaTypes;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.testkit.JUnitRouteTest;
import akka.http.javadsl.testkit.TestRoute;
import com.totorovan.transfer.account.AccountDto;
import com.totorovan.transfer.account.AccountFactory;
import com.totorovan.transfer.account.AccountInfo;
import com.totorovan.transfer.transaction.TransactionDto;
import com.totorovan.transfer.transaction.TransactionFactory;
import com.totorovan.transfer.transaction.TransactionInfo;
import org.junit.Before;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.ExecutionException;

import static akka.pattern.PatternsCS.ask;
import static com.totorovan.transfer.transaction.TransactionInfo.TransactionStatus.*;

public class RoutesTest extends JUnitRouteTest {

    private TestRoute appRoute;
    private Application app;
    private Duration timeout = Duration.ofSeconds(1);

    @Before
    public void before() {
        app = new Application(new AccountFactory(), new TransactionFactory(), "localhost:8080", Duration.ofSeconds(1));
        appRoute = testRoute(app.buildRoutes());
    }

    @org.junit.Test
    public void testGetNonExistingAccountReturnsNotFound() {
        appRoute.run(HttpRequest.GET("/accounts/1"))
                .assertStatusCode(StatusCodes.NOT_FOUND);
    }

    @org.junit.Test
    public void testGetExistingAccountReturnsIt() throws InterruptedException, ExecutionException {
        ask(app.getAccountService(), new AccountInfo(1L, BigDecimal.ZERO), timeout).toCompletableFuture().get();
        appRoute.run(HttpRequest.GET("/accounts/1"))
                .assertStatusCode(StatusCodes.OK)
                .assertEntityAs(Jackson.unmarshaller(AccountDto.class), new AccountDto(1L, BigDecimal.ZERO));
    }

    @org.junit.Test
    public void testPostNonExistingAccountReturnsOK() {
        appRoute.run(HttpRequest.POST("/accounts")
                .withEntity(MediaTypes.APPLICATION_JSON.toContentType(),
                        "{\"id\": 1, \"balance\": 0}"))
                .assertStatusCode(StatusCodes.CREATED);
    }

    @org.junit.Test
    public void testPostExistingAccountReturnsBadRequest() throws ExecutionException, InterruptedException {
        ask(app.getAccountService(), new AccountInfo(1L, BigDecimal.ZERO), timeout).toCompletableFuture().get();
        appRoute.run(HttpRequest.POST("/accounts")
                .withEntity(MediaTypes.APPLICATION_JSON.toContentType(),
                        "{\"id\": 1, \"balance\": 0}"))
                .assertStatusCode(StatusCodes.BAD_REQUEST);
    }

    @org.junit.Test
    public void testDeleteExistingAccountReturnsOK() throws ExecutionException, InterruptedException {
        ask(app.getAccountService(), new AccountInfo(1L, BigDecimal.ZERO), timeout).toCompletableFuture().get();
        appRoute.run(HttpRequest.DELETE("/accounts/1"))
                .assertStatusCode(StatusCodes.OK);
    }

    @org.junit.Test
    public void testDeleteNonExistingAccountReturnsNotFound() {
        appRoute.run(HttpRequest.DELETE("/accounts/1"))
                .assertStatusCode(StatusCodes.NOT_FOUND);
    }

    @org.junit.Test
    public void testGetNonExistingTransactionReturnsNotFound() {
        appRoute.run(HttpRequest.GET("/transactions/1"))
                .assertStatusCode(StatusCodes.NOT_FOUND);
    }

    @org.junit.Test
    public void testGetTransactionNotFound() throws ExecutionException, InterruptedException {
        ask(app.getTransactionService(), new TransactionInfo(1L, 1L, 2L, BigDecimal.ZERO, NEW), timeout).toCompletableFuture().get();
        appRoute.run(HttpRequest.GET("/transactions/1"))
                .assertStatusCode(StatusCodes.OK)
                .assertContentType(MediaTypes.APPLICATION_JSON.toContentType())
                .assertEntityAs(Jackson.unmarshaller(TransactionDto.class), new TransactionDto(1L, 1L, 2L, BigDecimal.ZERO, ROLLEDBACK, null));
    }

    @org.junit.Test
    public void testSuccessfulTransfer() throws ExecutionException, InterruptedException {
        ask(app.getAccountService(), new AccountInfo(1L, BigDecimal.ONE), timeout).toCompletableFuture().get();
        ask(app.getAccountService(), new AccountInfo(2L, BigDecimal.ZERO), timeout).toCompletableFuture().get();
        appRoute.run(HttpRequest.POST("/transactions")
                .withEntity(MediaTypes.APPLICATION_JSON.toContentType(),
                        "{\"id\": 1, \"srcAccountId\": 1, \"targetAccountId\": 2, \"amount\": 1, \"status\": \"NEW\"}"))
                .assertStatusCode(StatusCodes.CREATED)
                .assertEntityAs(Jackson.unmarshaller(TransactionDto.class), new TransactionDto(1L, 1L, 2L, BigDecimal.ONE, COMMITTED, null));
    }

    @org.junit.Test
    public void testFailureTransfer() throws ExecutionException, InterruptedException {
        ask(app.getAccountService(), new AccountInfo(1L, BigDecimal.ZERO), timeout).toCompletableFuture().get();
        ask(app.getAccountService(), new AccountInfo(2L, BigDecimal.ZERO), timeout).toCompletableFuture().get();
        appRoute.run(HttpRequest.POST("/transactions")
                .withEntity(MediaTypes.APPLICATION_JSON.toContentType(),
                        "{\"id\": 1, \"srcAccountId\": 1, \"targetAccountId\": 2, \"amount\": 1, \"status\": \"NEW\"}"))
                .assertStatusCode(StatusCodes.CREATED)
                .assertEntityAs(Jackson.unmarshaller(TransactionDto.class),
                        new TransactionDto(1L, 1L, 2L, BigDecimal.ONE, ROLLEDBACK,
                                "Insufficient balance to withdraw 1 from account AccountInfo(id=1, balance=0)"));
    }
}
