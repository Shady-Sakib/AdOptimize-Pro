package com.adoptimizer.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/** Writes small JSON error bodies from security filters, where controller advice does not apply. */
final class JsonResponses {

    private JsonResponses() {
    }

    static boolean isApiRequest(HttpServletRequest request) {
        return request.getServletPath().startsWith("/api/");
    }

    static void write(HttpServletResponse response, int status, String error, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"status\":" + status
                + ",\"error\":\"" + escape(error) + "\""
                + ",\"message\":\"" + escape(message) + "\""
                + ",\"fieldErrors\":{}"
                + ",\"timestamp\":\"" + LocalDateTime.now().withNano(0) + "\"}");
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
