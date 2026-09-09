package io.github.temporalrift.game.shared.infrastructure.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Bounds every request body to {@code maxBytes}, including a chunked body with no Content-Length
 * header, before any handler or {@code RequestBodyAdvice} reads it. Runs outside Spring Security
 * (registered via a plain {@link jakarta.servlet.Filter} bean, not the security filter chain) so an
 * oversized body is rejected before authentication spends any work on it.
 */
class RequestBodySizeLimitFilter extends OncePerRequestFilter {

    private final long maxBytes;

    RequestBodySizeLimitFilter(long maxBytes) {
        this.maxBytes = maxBytes;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > maxBytes) {
            response.sendError(
                    HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "Request body exceeds the maximum allowed size");
            return;
        }
        filterChain.doFilter(new BoundedBodyRequest(request, maxBytes), response);
    }

    private static final class BoundedBodyRequest extends HttpServletRequestWrapper {

        private final long maxBytes;

        BoundedBodyRequest(HttpServletRequest request, long maxBytes) {
            super(request);
            this.maxBytes = maxBytes;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            return new BoundedServletInputStream(super.getInputStream(), maxBytes);
        }

        @Override
        public BufferedReader getReader() throws IOException {
            var charset = getCharacterEncoding() != null ? getCharacterEncoding() : StandardCharsets.ISO_8859_1.name();
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }

    private static final class BoundedServletInputStream extends ServletInputStream {

        private final ServletInputStream delegate;
        private long remaining;

        BoundedServletInputStream(ServletInputStream delegate, long maxBytes) {
            this.delegate = delegate;
            this.remaining = maxBytes;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) {
                return rejectIfMoreDataExists();
            }
            var read = delegate.read();
            if (read != -1) {
                remaining--;
            }
            return read;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (len == 0) {
                return 0;
            }
            if (remaining <= 0) {
                return rejectIfMoreDataExists();
            }
            var bounded = delegate.read(b, off, (int) Math.min(len, remaining));
            if (bounded > 0) {
                remaining -= bounded;
            }
            return bounded;
        }

        /**
         * A body of exactly {@code maxBytes} must still read cleanly to EOF once the budget is
         * spent — only a body carrying at least one more byte beyond the limit is actually
         * oversized. Reads one probe byte from the delegate to tell the two cases apart.
         */
        private int rejectIfMoreDataExists() throws IOException {
            if (delegate.read() != -1) {
                throw new IOException("Request body exceeds the maximum allowed size");
            }
            return -1;
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            delegate.setReadListener(readListener);
        }
    }
}
