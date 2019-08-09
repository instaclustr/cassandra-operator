package com.instaclustr.io;

import java.io.IOException;
import java.nio.channels.SeekableByteChannel;

import com.google.common.base.Throwables;
import sun.nio.ch.ChannelInputStream;

/**
 * Extends ChannelInputStream to wrap a SeekableByteChannel where the
 * skip(), mark() and reset() methods operate on the underlying
 * SeekableByteChannel.
 *
 * @author Adam Zegelin
 */
public class SeekableByteChannelInputStream extends ChannelInputStream {
    public final SeekableByteChannel ch;
    public long markPos = -1;

    public SeekableByteChannelInputStream(final SeekableByteChannel channel) {
        super(channel);
        this.ch = channel;
    }

    @Override
    public long skip(final long n) throws IOException {
        final long position = Math.max(0, Math.min(ch.size(), ch.position() + n));
        final long skipped = Math.abs(position - ch.position());

        ch.position(position);

        return skipped;
    }

    @Override
    public synchronized void mark(final int readlimit) {
        try {
            markPos = ch.position();

        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public synchronized void reset() throws IOException {
        if (markPos < 0)
            throw new IOException("Resetting to invalid mark");

        ch.position(markPos);
    }

    @Override
    public boolean markSupported() {
        return true;
    }
}
