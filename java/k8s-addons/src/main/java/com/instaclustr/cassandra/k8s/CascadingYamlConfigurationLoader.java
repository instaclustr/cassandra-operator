package com.instaclustr.cassandra.k8s;

import org.apache.cassandra.config.Config;
import org.apache.cassandra.config.ConfigurationLoader;
import org.apache.cassandra.exceptions.ConfigurationException;

/**
 * A ConfigurationLoader
 */
public class CascadingYamlConfigurationLoader implements ConfigurationLoader {
    @Override
    public Config loadConfig() throws ConfigurationException {
        return null;
    }
}
