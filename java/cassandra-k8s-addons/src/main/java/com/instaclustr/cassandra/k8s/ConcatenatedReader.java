package com.instaclustr.cassandra.k8s;

import java.io.IOException;
import java.io.Reader;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Queue;

public class ConcatenatedReader extends Reader {

    private final Queue<Reader> readers;

    public ConcatenatedReader(final Collection<? extends Reader> readers) {
        this.readers = new LinkedList<>(readers);
    }

    @Override
    public int read(final char[] cbuf, final int off, final int len) throws IOException {
        if (readers.isEmpty()) {
            return -1;
        }

        final Reader currentReader = readers.peek();

        final int bytesRead = currentReader.read(cbuf, off, len);

        if (bytesRead != -1) {
            return bytesRead;
        }

        currentReader.close();
        readers.remove();

        // return new-lines between files
        cbuf[off] = '\n';
        return 1;
    }

    @Override
    public void close() throws IOException {
        for (final Reader reader : readers) {
            reader.close();
        }
    }
}