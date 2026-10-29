package br.com.nevvesdev.alcada.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    private final long maxBytes;

    public RequestSizeLimitFilter(@Value("${alcada.api.max-request-bytes:16384}") long maxBytes) {
        if (maxBytes < 1) {
            throw new IllegalArgumentException("alcada.api.max-request-bytes deve ser maior ou igual a 1");
        }
        this.maxBytes = maxBytes;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (request.getContentLengthLong() > maxBytes) {
            reject(response);
            return;
        }
        chain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(413);
        response.setContentType("application/problem+json;charset=UTF-8");
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Payload muito grande\","
                + "\"status\":413,\"detail\":\"O corpo da requisição excede o limite de " + maxBytes
                + " bytes\",\"code\":\"PAYLOAD_TOO_LARGE\"}");
    }
}