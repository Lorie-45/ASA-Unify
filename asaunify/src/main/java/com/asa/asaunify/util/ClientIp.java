package com.asa.asaunify.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Single source for the caller's IP. Uses the socket address only: the
 * X-Forwarded-For header is client-controlled, so trusting it let anyone dodge
 * the per-IP login/reset throttles and forge audit entries. Behind a reverse
 * proxy, enable the container's trusted-proxy handling instead (see
 * server.forward-headers-strategy in application.properties) so
 * getRemoteAddr() reflects the real client.
 */
public final class ClientIp {

    private ClientIp() {
    }

    public static String of(HttpServletRequest request) {
        if (request == null) return "unknown";
        String ip = request.getRemoteAddr();
        return ip != null ? ip : "unknown";
    }
}
