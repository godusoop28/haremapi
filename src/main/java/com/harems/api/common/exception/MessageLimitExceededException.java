package com.harems.api.common.exception;

public class MessageLimitExceededException extends RuntimeException {

    public MessageLimitExceededException(String message) {
        super(message);
    }
}
