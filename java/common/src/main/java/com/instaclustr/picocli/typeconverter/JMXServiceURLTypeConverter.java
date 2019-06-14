package com.instaclustr.picocli.typeconverter;

import javax.management.remote.JMXServiceURL;
import java.net.MalformedURLException;

import picocli.CommandLine;

public abstract class JMXServiceURLTypeConverter extends HostnamePortConverter implements CommandLine.ITypeConverter<JMXServiceURL> {
    @Override
    public JMXServiceURL convert(final String value) throws Exception {
        try {
            final HostnameAndPort hostnameAndPort = getHostnameAndPort(value);
            return new JMXServiceURL(String.format("service:jmx:rmi:///jndi/rmi://%s:%s/jmxrmi", hostnameAndPort.hostname, hostnameAndPort.port));
        } catch (final MalformedURLException e) {
            throw new CommandLine.TypeConversionException("Invalid JMX service URL (" + e.getLocalizedMessage() + ")");
        }
    }
}
