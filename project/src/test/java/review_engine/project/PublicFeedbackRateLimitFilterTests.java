package review_engine.project;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import review_engine.project.config.FeedbackProperties;
import review_engine.project.config.PublicFeedbackRateLimitFilter;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PublicFeedbackRateLimitFilterTests {
    @Test
    void appliesConfiguredPerIpLimitAndAllowsRequestsInTheNextWindow() throws Exception {
        FeedbackProperties properties = new FeedbackProperties();
        properties.setRateLimitPerMinute(2);
        ObjectMapper mapper = mock(ObjectMapper.class);
        PublicFeedbackRateLimitFilter filter = new PublicFeedbackRateLimitFilter(properties, mapper,
                Clock.fixed(Instant.parse("2026-02-01T12:00:10Z"), ZoneOffset.UTC));

        assertEquals(200, invoke(filter));
        assertEquals(200, invoke(filter));
        MockHttpServletResponse limited = responseFor(filter);
        assertEquals(429, limited.getStatus());
        assertEquals("50", limited.getHeader("Retry-After"));
        verify(mapper).writeValue(any(java.io.OutputStream.class), any(Object.class));
    }

    private int invoke(PublicFeedbackRateLimitFilter filter) throws Exception {
        return responseFor(filter).getStatus();
    }

    private MockHttpServletResponse responseFor(PublicFeedbackRateLimitFilter filter) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/public/feedback/token");
        request.setServletPath("/api/public/feedback/token");
        request.setRemoteAddr("192.0.2.10");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
