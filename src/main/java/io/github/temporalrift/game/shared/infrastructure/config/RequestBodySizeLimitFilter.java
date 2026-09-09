package io.github.temporalrift.game.shared.infrastructure.config;

import java.io.IOException;

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
                throw new IOException("Request body exceeds the maximum allowed size");
            }
            var read = delegate.read();
            if (read != -1) {
                remaining--;
            }
            return read;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (remaining <= 0) {
                throw new IOException("Request body exceeds the maximum allowed size");
            }
            var bounded = delegate.read(b, off, (int) Math.min(len, remaining));
            if (bounded > 0) {
                remaining -= bounded;
            }
            return bounded;
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
