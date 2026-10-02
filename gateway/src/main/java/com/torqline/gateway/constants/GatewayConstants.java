package com.torqline.gateway.constants;

public final class GatewayConstants {

    /** Correlation id added to every request and response; same value as ApiHeaders.REQUEST_ID in torqline-common. */
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    private GatewayConstants() {
    }
}
