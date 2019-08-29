package com.instaclustr.io;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

import com.google.common.util.concurrent.RateLimiter;

public class RateLimitedInputStream extends FilterInputStream {
    final RateLimiter limiter;

    public RateLimitedInputStream(final InputStream in, final RateLimiter limiter) {
        super(in);
        this.limiter = limiter;
    }

    @Override
    public int read() throws IOException {
        limiter.acquire();
        return super.read();
    }

    @Override
    public int read(final byte[] b) throws IOException {
        limiter.acquire(Math.max(1, b.length));
        return super.read(b);
    }

    @Override
    public int read(final byte[] b, final int off, final int len) throws IOException {
        limiter.acquire(Math.max(1, len));
        return super.read(b, off, len);
    }
}