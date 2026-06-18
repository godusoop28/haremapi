package com.harems.api.common.exception;

/**
 * Thrown when an image provider (e.g. fal.ai) blocks or rejects the content.
 * The service layer catches this to refund credits before rethrowing.
 * The GlobalExceptionHandler converts it to HTTP 422 with code IMAGE_PROVIDER_BLOCKED.
 */
public class ImageGenerationBlockedException extends RuntimeException {
    public ImageGenerationBlockedException(String message) {
        super(message);
    }
}
