package com.totorovan.transfer.common;

import lombok.Data;

import java.io.Serializable;

public class Messages {

    private Messages() {
    }

    @Data
    public static class Success implements Serializable {
    }

    @Data
    public static class Failure implements Serializable {
        private final String message;
    }
}
