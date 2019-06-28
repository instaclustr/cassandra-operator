package com.instaclustr.cassandra.backup.model;

import javax.management.remote.JMXServiceURL;
import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

public class JMXServiceURLSerializer extends StdSerializer<JMXServiceURL> {

    public JMXServiceURLSerializer() {
        this(null);
    }

    public JMXServiceURLSerializer(Class<JMXServiceURL> t) {
        super(t);
    }

    @Override
    public void serialize(JMXServiceURL value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        gen.writeString(value.toString());
    }
}
