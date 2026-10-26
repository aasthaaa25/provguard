package com.provguard.core.exception;

public class EventLoggingException extends RuntimeException {
    public EventLoggingException(String message) {
        super(message);
    }

    public EventLoggingException(String message, Throwable cause) {
        super(message, cause);
    }
}
