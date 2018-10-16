package com.totorovan.transfer.transaction;

import com.totorovan.transfer.transaction.TransactionInfo.TransactionStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TransactionDto {
    private long id;
    private long srcAccountId;
    private long targetAccountId;
    private BigDecimal amount;
    private TransactionStatus status;
    private String statusReason;
}
