package com.torqline.common.constants;

/** HTTP header names that are part of the public API. */
public final class ApiHeaders {
    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    public static final String REQUEST_ID = "X-Request-Id";

    private ApiHeaders() {
    }
}
