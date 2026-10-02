package com.torqline.common.constants;

/** Error codes shared by every service. Service-specific codes live in each service's constants package. */
public final class ErrorCodes {
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final String CONCURRENT_MODIFICATION = "CONCURRENT_MODIFICATION";

    private ErrorCodes() {
    }
}
