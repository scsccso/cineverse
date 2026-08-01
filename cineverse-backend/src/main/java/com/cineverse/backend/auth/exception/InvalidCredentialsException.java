package com.cineverse.backend.auth.exception;

/**
 * Thrown for both "email not found" and "wrong password" — deliberately
 * the same exception with the same message so the API response can't be
 * used to enumerate registered email addresses.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
