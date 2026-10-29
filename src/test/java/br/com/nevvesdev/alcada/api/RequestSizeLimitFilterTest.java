package br.com.nevvesdev.alcada.api;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestSizeLimitFilterTest {

    private final RequestSizeLimitFilter filter = new RequestSizeLimitFilter(100);

    private static MockHttpServletRequest requestWithBody(int bytes) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/credit-requests");
        request.setContent(new byte[bytes]);
        return request;
    }

    @Test
    void shouldAllowBodyAtTheLimit() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(requestWithBody(100), response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void shouldRejectBodyAboveTheLimitWithoutCallingTheChain() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(requestWithBody(101), response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(response.getContentAsString()).contains("PAYLOAD_TOO_LARGE", "100 bytes");
    }

    @Test
    void shouldAllowRequestsWithoutBody() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/credit-requests/1"),
                new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void shouldRejectInvalidLimit() {
        assertThatThrownBy(() -> new RequestSizeLimitFilter(0)).isInstanceOf(IllegalArgumentException.class);
    }
}