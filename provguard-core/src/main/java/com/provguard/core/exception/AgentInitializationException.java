package com.provguard.core.exception;

public class AgentInitializationException extends RuntimeException {
    public AgentInitializationException(String message) {
        super(message);
    }

    public AgentInitializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
