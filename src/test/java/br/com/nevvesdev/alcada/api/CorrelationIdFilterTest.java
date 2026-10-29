package br.com.nevvesdev.alcada.api;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    private String run(String headerValue, MockHttpServletResponse response, AtomicReference<String> seenInChain)
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (headerValue != null) {
            request.addHeader("X-Correlation-Id", headerValue);
        }
        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                seenInChain.set(MDC.get("correlationId")));
        return response.getHeader("X-Correlation-Id");
    }

    @Test
    void shouldReuseValidCorrelationIdAndExposeItToTheLogContext() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seenInChain = new AtomicReference<>();

        String echoed = run("trace-123", response, seenInChain);

        assertThat(echoed).isEqualTo("trace-123");
        assertThat(seenInChain.get()).isEqualTo("trace-123");
    }

    @Test
    void shouldGenerateCorrelationIdWhenMissing() throws Exception {
        String echoed = run(null, new MockHttpServletResponse(), new AtomicReference<>());

        assertThat(echoed).hasSize(36);
    }

    @Test
    void shouldReplaceCorrelationIdWithUnsafeCharacters() throws Exception {
        String echoed = run("abc\nforged-line", new MockHttpServletResponse(), new AtomicReference<>());

        assertThat(echoed).isNotEqualTo("abc\nforged-line").hasSize(36);
    }

    @Test
    void shouldReplaceCorrelationIdThatIsTooLong() throws Exception {
        String tooLong = "a".repeat(65);

        String echoed = run(tooLong, new MockHttpServletResponse(), new AtomicReference<>());

        assertThat(echoed).isNotEqualTo(tooLong).hasSize(36);
    }

    @Test
    void shouldClearTheLogContextAfterTheRequest() throws Exception {
        run("trace-123", new MockHttpServletResponse(), new AtomicReference<>());

        assertThat(MDC.get("correlationId")).isNull();
    }
}