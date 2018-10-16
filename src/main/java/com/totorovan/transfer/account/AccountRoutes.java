package com.totorovan.transfer.account;

import akka.actor.ActorRef;
import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.AllDirectives;
import akka.http.javadsl.server.PathMatchers;
import akka.http.javadsl.server.Route;
import com.totorovan.transfer.common.Messages.Failure;
import com.totorovan.transfer.common.Messages.Success;
import lombok.RequiredArgsConstructor;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

import static akka.pattern.PatternsCS.ask;
import static io.vavr.API.*;
import static io.vavr.API.Match.Pattern0.any;
import static io.vavr.Predicates.instanceOf;

@RequiredArgsConstructor
public class AccountRoutes extends AllDirectives {

    private final ActorRef accountService;
    private final Duration timeout;

    private static AccountInfo mapToAccountInfo(AccountDto accountDto) {
        return new AccountInfo(accountDto.getId(), accountDto.getBalance());
    }

    private static AccountDto mapToAccountDto(AccountInfo accountInfo) {
        return new AccountDto(accountInfo.getId(), accountInfo.getBalance());
    }

    public Route routes() {
        return pathPrefix("accounts", () ->
                route(
                        postAccount(),
                        path(PathMatchers.longSegment(), id ->
                                route(
                                        getAccount(id),
                                        deleteAccount(id)
                                )
                        )
                )
        );
    }

    private Route postAccount() {
        return pathEnd(() ->
                post(() ->
                        entity(Jackson.unmarshaller(AccountDto.class), accountDto -> {
                            CompletionStage<Object> createAccountResponse = ask(accountService, mapToAccountInfo(accountDto), timeout);
                            return onSuccess(createAccountResponse, this::handleCreateAccountResponse);
                        })
                )
        );
    }

    private Route handleCreateAccountResponse(Object createAccountResponse) {
        return Match(createAccountResponse).of(
                Case($(instanceOf(Success.class)), success -> complete(StatusCodes.CREATED)),
                Case($(instanceOf(Failure.class)), failure -> complete(StatusCodes.BAD_REQUEST, failure, Jackson.marshaller())),
                Case($(any()), x -> complete(StatusCodes.INTERNAL_SERVER_ERROR))
        );
    }

    private Route getAccount(long id) {
        return get(() -> {
            CompletionStage<Object> getAccountInfoResponse = ask(accountService, new Account.GetAccount(id), timeout);
            return onSuccess(() -> getAccountInfoResponse, this::handleGetAccountInfoResponse);
        });
    }

    private Route handleGetAccountInfoResponse(Object getAccountInfoResponse) {
        return Match(getAccountInfoResponse).of(
                Case($(instanceOf(AccountInfo.class)), accountInfo -> complete(StatusCodes.OK, mapToAccountDto(accountInfo), Jackson.marshaller())),
                Case($(instanceOf(Failure.class)), failure -> complete(StatusCodes.NOT_FOUND, failure, Jackson.marshaller())),
                Case($(any()), x -> complete(StatusCodes.INTERNAL_SERVER_ERROR))
        );
    }

    private Route deleteAccount(long id) {
        return delete(() -> {
            CompletionStage<Object> deleteAccountResponse = ask(accountService, new AccountService.DeleteAccount(id), timeout);
            return onSuccess(() -> deleteAccountResponse, this::handleDeleteAccountResponse);
        });
    }

    private Route handleDeleteAccountResponse(Object deleteAccountResponse) {
        return Match(deleteAccountResponse).of(
                Case($(instanceOf(Success.class)), success -> complete(StatusCodes.OK)),
                Case($(instanceOf(Failure.class)), failure -> complete(StatusCodes.NOT_FOUND, failure, Jackson.marshaller())),
                Case($(any()), x -> complete(StatusCodes.INTERNAL_SERVER_ERROR))
        );
    }
}
