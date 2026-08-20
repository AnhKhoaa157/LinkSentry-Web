package com.lyanhkhoa.linksentry.admin.application;

/** Non-enumerating admin login failure. */
public class InvalidAdminCredentialsException extends RuntimeException {

    public InvalidAdminCredentialsException() {
        super("Username or password is incorrect.");
    }
}
