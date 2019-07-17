package com.instaclustr.jackson;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

public class PathDeserializer extends StdDeserializer<Path> {

    public PathDeserializer() {
        super(Path.class);
    }

    @Override
    public Path deserialize(final JsonParser p, final DeserializationContext ctxt) throws IOException {
        if (p.getValueAsString() != null) {
            return Paths.get(p.getValueAsString());
        } else {
            return null;
        }
    }
}
