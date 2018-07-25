package com.instaclustr.backup.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import javax.management.remote.JMXServiceURL;
import java.io.IOException;

public class JMXServiceURLDeserializer extends StdDeserializer<JMXServiceURL> {

    public JMXServiceURLDeserializer() {
        this(null);
    }


    public JMXServiceURLDeserializer(Class<JMXServiceURL> vc) {
        super(vc);
    }

    @Override
    public JMXServiceURL deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        return new JMXServiceURL(p.getValueAsString());
    }
}
