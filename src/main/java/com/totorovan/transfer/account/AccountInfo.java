package com.totorovan.transfer.account;

import lombok.Value;

import java.io.Serializable;
import java.math.BigDecimal;

@Value
public class AccountInfo implements Serializable {
    private final long id;
    private final BigDecimal balance;
}
