package com.github.piantino.keycloak.exception;

public class DbUserProviderException extends RuntimeException {


    public DbUserProviderException(String message) {
        super(message);
    }

    public DbUserProviderException(String message, Throwable cause) {
        super(message, cause);
    }
    
}
