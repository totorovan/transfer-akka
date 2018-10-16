package com.totorovan.transfer.transaction;

import akka.actor.ActorRef;
import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.AllDirectives;
import akka.http.javadsl.server.PathMatchers;
import akka.http.javadsl.server.Route;
import com.totorovan.transfer.common.Messages.Failure;
import com.totorovan.transfer.transaction.TransactionService.TransactionRolledBack;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.CompletionStage;

import static akka.pattern.PatternsCS.ask;
import static io.vavr.API.*;
import static io.vavr.API.Match.Pattern0.any;
import static io.vavr.Predicates.instanceOf;

@RequiredArgsConstructor
public class TransactionRoutes extends AllDirectives {

    private final ActorRef transactionService;
    private final Duration timeout;

    private static TransactionInfo mapToTransactionInfo(TransactionDto transactionDto) {
        return new TransactionInfo(transactionDto.getId(), transactionDto.getSrcAccountId(),
                transactionDto.getTargetAccountId(), transactionDto.getAmount(), transactionDto.getStatus());
    }

    private static TransactionDto mapToTransactionDto(TransactionInfo transactionInfo, String reason) {
        return new TransactionDto(transactionInfo.getId(), transactionInfo.getSrcAccountId(), transactionInfo.getTargetAccountId(),
                transactionInfo.getAmount(), transactionInfo.getStatus(), reason);
    }

    public Route routes() {
        return route(pathPrefix("transactions", () ->
                route(
                        postTransaction(),
                        path(PathMatchers.longSegment(), id ->
                                route(
                                        getTransaction(id),
                                        deleteTransaction(id)
                                )
                        )
                )));
    }

    private Route postTransaction() {
        return pathEnd(() ->
                post(() -> entity(Jackson.unmarshaller(TransactionDto.class), this::transfer))
        );
    }

    private Route transfer(TransactionDto transactionDto) {
        BigDecimal amount = transactionDto.getAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            return complete(StatusCodes.BAD_REQUEST, "Amount can not be null or less than zero");
        }
        CompletionStage<Object> transferResponse = ask(transactionService, mapToTransactionInfo(transactionDto), timeout);
        return onSuccess(transferResponse, this::handleTransferResponse);
    }

    private Route handleTransferResponse(Object transferResponse) {
        return Match(transferResponse).of(
                Case($(instanceOf(Failure.class)), failure -> complete(StatusCodes.BAD_REQUEST, failure, Jackson.marshaller())),
                Case($(instanceOf(TransactionInfo.class)), transaction -> complete(StatusCodes.CREATED, mapToTransactionDto(transaction, null), Jackson.marshaller())),
                Case($(instanceOf(TransactionRolledBack.class)), rollback -> complete(StatusCodes.CREATED,
                        mapToTransactionDto(rollback.getTransactionInfo(), rollback.getReason()), Jackson.marshaller())),
                Case($(any()), x -> complete(StatusCodes.INTERNAL_SERVER_ERROR))

        );
    }

    private Route getTransaction(long id) {
        return get(() -> {
            CompletionStage<Object> getTransactionResponse = ask(transactionService, new Transaction.GetTransaction(id), timeout);
            return onSuccess(() -> getTransactionResponse, this::handleGetTransactionResponse);
        });
    }

    private Route handleGetTransactionResponse(Object response) {
        return Match(response).of(
                Case($(instanceOf(Failure.class)), failure -> complete(StatusCodes.NOT_FOUND, failure, Jackson.marshaller())),
                Case($(instanceOf(TransactionInfo.class)), transaction -> complete(StatusCodes.OK, transaction, Jackson.marshaller())),
                Case($(any()), x -> complete(StatusCodes.INTERNAL_SERVER_ERROR))
        );
    }

    private Route deleteTransaction(long id) {
        return delete(() -> {
            CompletionStage<Object> deleteResponse = ask(transactionService, new TransactionService.DeleteTransaction(id), timeout);
            return onSuccess(() -> deleteResponse, this::handleDeleteResponse);
        });
    }

    private Route handleDeleteResponse(Object deleteResponse) {
        return Match(deleteResponse).of(
                Case($(instanceOf(Failure.class)), failure -> complete(StatusCodes.NOT_FOUND, deleteResponse, Jackson.marshaller())),
                Case($(any()), x -> complete(StatusCodes.OK))
        );
    }
}
