package com.totorovan.transfer.transaction;

import akka.actor.ActorContext;
import akka.actor.ActorRef;

public class TransactionFactory {
    ActorRef get(ActorContext context, TransactionInfo transactionInfo) {
        return context.actorOf(Transaction.props(transactionInfo));
    }
}
