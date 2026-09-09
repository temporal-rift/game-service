package io.github.temporalrift.game.shared.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RequestBodySizeLimitFilter")
class RequestBodySizeLimitFilterTest {

    @Test
    @DisplayName("Content-Length over the limit — responds 413 without invoking the chain")
    void rejectsOversizedContentLength() throws Exception {
        var request = mock(HttpServletRequest.class);
        var response = mock(HttpServletResponse.class);
        var chain = mock(FilterChain.class);
        given(request.getContentLengthLong()).willReturn(2048L);

        new RequestBodySizeLimitFilter(1024).doFilterInternal(request, response, chain);

        then(response).should().sendError(eq(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE), anyString());
        then(chain).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("no Content-Length header — a chunked body exceeding the limit fails while being read, not before")
    void rejectsChunkedBodyExceedingLimitDuringRead() throws Exception {
        var payload = "x".repeat(2048).getBytes(StandardCharsets.UTF_8);
        var request = mock(HttpServletRequest.class);
        var response = mock(HttpServletResponse.class);
        given(request.getContentLengthLong()).willReturn(-1L);
        given(request.getInputStream()).willReturn(new StubServletInputStream(payload));
        var wrappedRequest = new AtomicReference<HttpServletRequest>();
        FilterChain chain = (req, res) -> wrappedRequest.set((HttpServletRequest) req);

        new RequestBodySizeLimitFilter(1024).doFilterInternal(request, response, chain);

        assertThat(wrappedRequest.get()).isNotNull();
        var boundedInput = wrappedRequest.get().getInputStream();
        assertThatThrownBy(boundedInput::readAllBytes).isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("no Content-Length header — a body within the limit is read through unchanged")
    void passesThroughBodyWithinLimit() throws Exception {
        var payload = "small body".getBytes(StandardCharsets.UTF_8);
        var request = mock(HttpServletRequest.class);
        var response = mock(HttpServletResponse.class);
        given(request.getContentLengthLong()).willReturn(-1L);
        given(request.getInputStream()).willReturn(new StubServletInputStream(payload));
        var wrappedRequest = new AtomicReference<HttpServletRequest>();
        FilterChain chain = (req, res) -> wrappedRequest.set((HttpServletRequest) req);

        new RequestBodySizeLimitFilter(1024).doFilterInternal(request, response, chain);

        assertThat(wrappedRequest.get().getInputStream().readAllBytes()).isEqualTo(payload);
    }

    private static final class StubServletInputStream extends ServletInputStream {

        private final ByteArrayInputStream delegate;

        StubServletInputStream(byte[] data) {
            this.delegate = new ByteArrayInputStream(data);
        }

        @Override
        public int read() {
            return delegate.read();
        }

        @Override
        public int read(byte[] b, int off, int len) {
            return delegate.read(b, off, len);
        }

        @Override
        public boolean isFinished() {
            return delegate.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {}
    }
}
