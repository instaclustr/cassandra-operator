package com.instaclustr.jackson;

import java.io.IOException;
import java.nio.file.Path;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

public class PathSerializer extends StdSerializer<Path> {

    public PathSerializer() {
        super(Path.class);
    }

    @Override
    public void serialize(final Path value, final JsonGenerator gen, final SerializerProvider provider) throws IOException {
        if (value != null) {
            gen.writeString(value.toString());
        }
    }
}
