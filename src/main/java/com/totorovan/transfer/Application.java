package com.totorovan.transfer;

import akka.NotUsed;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.server.AllDirectives;
import akka.http.javadsl.server.Route;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Flow;
import com.totorovan.transfer.account.AccountFactory;
import com.totorovan.transfer.account.AccountRoutes;
import com.totorovan.transfer.account.AccountService;
import com.totorovan.transfer.transaction.TransactionFactory;
import com.totorovan.transfer.transaction.TransactionRoutes;
import com.totorovan.transfer.transaction.TransactionService;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletionStage;

public class Application extends AllDirectives {
    private final LoggingAdapter log;
    private final ActorRef accountService;
    private final ActorRef transactionService;
    private final ActorSystem system = ActorSystem.create("transfer");
    private final String address;
    private final Duration timeout;

    Application(AccountFactory accountFactory, TransactionFactory transactionFactory, String address, Duration timeout) {
        accountService = system.actorOf(AccountService.props(accountFactory, timeout), "accountService");
        transactionService = system.actorOf(TransactionService.props(accountService, transactionFactory, timeout), "transactionService");
        this.timeout = timeout;
        this.address = address;
        this.log = Logging.getLogger(system, this);
    }

    public static void main(String[] args) throws IOException {
        Config conf = ConfigFactory.load();
        String address = conf.hasPath("server.address") ? conf.getString("server.address") : "localhost:8080";
        Duration timeout = conf.hasPath("actor.timeout") ? Duration.parse(conf.getString("actor.timeout")) : Duration.ofSeconds(1);

        Application application = new Application(new AccountFactory(), new TransactionFactory(), address, timeout);
        CompletionStage<ServerBinding> binding = application.createServerBinding();

        application.log.info("Server online at {}\nPress RETURN to stop...", application.address);
        System.in.read();

        binding
                .thenCompose(ServerBinding::unbind)
                .thenAccept(unbound -> application.system.terminate());
    }

    public ActorRef getAccountService() {
        return accountService;
    }

    public ActorRef getTransactionService() {
        return transactionService;
    }

    private CompletionStage<ServerBinding> createServerBinding() {
        ActorMaterializer materializer = ActorMaterializer.create(system);
        Flow<HttpRequest, HttpResponse, NotUsed> routeFlow = buildRoutes().flow(system, materializer);
        Http http = Http.get(system);

        return http.bindAndHandle(routeFlow,
                ConnectHttp.toHost(address), materializer);
    }

    Route buildRoutes() {
        return route(new AccountRoutes(accountService, timeout).routes(), new TransactionRoutes(transactionService, timeout).routes());
    }

}
