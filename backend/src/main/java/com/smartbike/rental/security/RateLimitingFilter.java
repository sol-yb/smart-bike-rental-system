package com.smartbike.rental.security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitingFilter implements Filter {

    private final Map<String, TokenBucket> limiters = new ConcurrentHashMap<>();
    
    // Limits: Max 60 requests, refilling 1 token every second (60 per minute)
    private static final int MAX_CAPACITY = 60;
    private static final double REFILL_RATE_PER_SECOND = 1.0;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // Initialization if needed
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        if (request instanceof HttpServletRequest httpRequest && response instanceof HttpServletResponse httpResponse) {
            String ip = getClientIP(httpRequest);
            
            // Bypass internal docker endpoints or standard resources if necessary
            String path = httpRequest.getRequestURI();
            if (path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs")) {
                chain.doFilter(request, response);
                return;
            }

            TokenBucket bucket = limiters.computeIfAbsent(ip, k -> new TokenBucket(MAX_CAPACITY, REFILL_RATE_PER_SECOND));

            if (!bucket.tryConsume(1)) {
                httpResponse.setStatus(429); // Too Many Requests
                httpResponse.setContentType("application/json");
                httpResponse.getWriter().write("{\"error\": \"Too many requests. Rate limit exceeded. Please try again in a few seconds.\"}");
                return;
            }
        }
        
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
        // Cleanup if needed
    }

    private String getClientIP(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0];
    }

    // In-memory token bucket rate limiter implementation
    private static class TokenBucket {
        private final int capacity;
        private final double refillRatePerSecond;
        private double tokens;
        private long lastRefillTimestamp;

        public TokenBucket(int capacity, double refillRatePerSecond) {
            this.capacity = capacity;
            this.refillRatePerSecond = refillRatePerSecond;
            this.tokens = capacity;
            this.lastRefillTimestamp = System.nanoTime();
        }

        public synchronized boolean tryConsume(int count) {
            refill();
            if (tokens >= count) {
                tokens -= count;
                return true;
            }
            return false;
        }

        private void refill() {
            long now = System.nanoTime();
            double elapsedSeconds = (now - lastRefillTimestamp) / 1_000_000_000.0;
            
            if (elapsedSeconds > 0) {
                double tokensToAdd = elapsedSeconds * refillRatePerSecond;
                tokens = Math.min(capacity, tokens + tokensToAdd);
                lastRefillTimestamp = now;
            }
        }
    }
}
