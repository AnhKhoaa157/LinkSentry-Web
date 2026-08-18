package com.lyanhkhoa.linksentry.auth.application;

/** Non-enumerating login failure. */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Email or password is incorrect.");
    }
}
