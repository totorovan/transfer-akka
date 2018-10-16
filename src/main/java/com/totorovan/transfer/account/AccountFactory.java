package com.totorovan.transfer.account;

import akka.actor.ActorContext;
import akka.actor.ActorRef;

public class AccountFactory {
    ActorRef get(ActorContext context, AccountInfo accountInfo) {
        return context.actorOf(Account.props(accountInfo), "account_" + accountInfo.getId());
    }
}
