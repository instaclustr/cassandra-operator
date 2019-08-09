package com.instaclustr.jackson;

import javax.management.remote.JMXServiceURL;
import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

public class JMXServiceURLDeserializer extends StdDeserializer<JMXServiceURL> {

    public JMXServiceURLDeserializer() {
        this(null);
    }

    public JMXServiceURLDeserializer(Class<JMXServiceURL> vc) {
        super(vc);
    }

    @Override
    public JMXServiceURL deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        if (p.getValueAsString() != null) {
            return new JMXServiceURL(p.getValueAsString());
        } else {
            return null;
        }
    }
}
