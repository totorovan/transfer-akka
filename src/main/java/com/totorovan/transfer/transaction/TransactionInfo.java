package com.totorovan.transfer.transaction;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class TransactionInfo implements Serializable {

    private final long id;
    private final long srcAccountId;
    private final long targetAccountId;
    private final BigDecimal amount;
    private final TransactionStatus status;

    public enum TransactionStatus {
        NEW, COMMITTED, ROLLEDBACK
    }
}
