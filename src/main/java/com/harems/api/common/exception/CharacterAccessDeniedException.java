package com.harems.api.common.exception;

public class CharacterAccessDeniedException extends RuntimeException {

    public CharacterAccessDeniedException(String message) {
        super(message);
    }
}
