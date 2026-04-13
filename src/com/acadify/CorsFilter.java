package com.acadify;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;

public class CorsFilter extends Filter {

    private static final String ALLOWED_ORIGIN = "http://localhost:3000";

    @Override
    public void doFilter(HttpExchange exchange, Chain chain) throws IOException {

        // ✅ Add CORS headers to EVERY response
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", ALLOWED_ORIGIN);
        exchange.getResponseHeaders().set("Access-Control-Allow-Credentials", "true");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");

        // ✅ Handle preflight OPTIONS request — browser sends this before POST/PUT
        if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
            exchange.sendResponseHeaders(204, -1); // 204 No Content
            return;
        }

        // ✅ Continue to actual handler
        chain.doFilter(exchange);
    }

    @Override
    public String description() {
        return "CORS Filter";
    }
}