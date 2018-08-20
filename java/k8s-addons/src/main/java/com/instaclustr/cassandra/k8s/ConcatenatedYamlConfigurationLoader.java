package com.instaclustr.cassandra.k8s;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.cassandra.config.Config;
import org.apache.cassandra.config.ConfigurationLoader;
import org.apache.cassandra.config.ParameterizedClass;
import org.apache.cassandra.config.YamlConfigurationLoader;
import org.apache.cassandra.exceptions.ConfigurationException;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.introspector.MissingProperty;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.introspector.PropertyUtils;

import java.beans.IntrospectionException;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * A ConfigurationLoader that reads from one or more specified YAML-formatted configuration files, including files
 * contained within any specified directories. The contents of these configuration files will be concatenated
 * together and then read as if they were a single file. Files are concatenated in the order specified, and files
 * in any specified directories will be concatenated in lexicographical order.
 */
public class ConcatenatedYamlConfigurationLoader implements ConfigurationLoader {
    private static final Logger logger = LoggerFactory.getLogger(ConcatenatedYamlConfigurationLoader.class);

    static class ConcatenatedReader extends Reader {
        private final Queue<Reader> readers;

        ConcatenatedReader(final Collection<? extends Reader> readers) {
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

    @Override
    public Config loadConfig() throws ConfigurationException {
        final String configProperty = System.getProperty("cassandra.config");
        logger.info("Loading config from {}", configProperty);
        final Iterable<String> configValues = Splitter.on(':').split(configProperty);

        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:**/*.{yaml,yml}");

        final List<BufferedReader> readers = StreamSupport.stream(configValues.spliterator(), false)
                .map(Paths::get)

                // recurse into any specified directories and load any config files within
                .flatMap(path -> {
                    if (!Files.exists(path)) {
                        throw new ConfigurationException(String.format("Specified configuration file/directory \"%s\" does not exist.", path));
                    }

                    if (Files.isDirectory(path)) {
                        try {
                            return Files.list(path)
                                    .sorted();

                        } catch (final IOException e) {
                            throw new ConfigurationException(String.format("Failed to open directory \"%s\".", path), e);
                        }

                    } else {
                        return Stream.of(path);
                    }
                })

                // only load regular yaml files
                .filter(path -> {
                    if (!Files.isRegularFile(path)) {
                        logger.warn("Configuration file \"{}\" is not a regular file and will not be loaded.", path);
                        return false;
                    }
                    return true;
                })

                .filter(path -> {
                    if(!matcher.matches(path)) {
                        logger.warn("Configuration file \"{}\" is not a yaml file and will not be loaded.", path);
                        return false;
                    }
                    logger.info("Loading configuration file \"{}\"", path);
                    return true;
                })

                .map(path -> {
                    try {
                        return Files.newBufferedReader(path, StandardCharsets.UTF_8);

                    } catch (final IOException e) {
                        throw new ConfigurationException(String.format("Failed to open configuration file \"%s\" for reading.", path), e);
                    }
                })
                .collect(Collectors.toList());


        try (final Reader reader = new ConcatenatedReader(readers)) {
            //Largely copied from YamlConfigurationLoader in C*
            Constructor constructor = new CustomConstructor(Config.class);
            PropertiesChecker propertiesChecker = new PropertiesChecker();
            constructor.setPropertyUtils(propertiesChecker);
            Yaml yaml = new Yaml(constructor);

            Config config = yaml.loadAs(reader, Config.class);
            // If the configuration file is empty yaml will return null. In this case we should use the default
            // configuration to avoid hitting a NPE at a later stage.
            config = config == null ? new Config() : config;

            propertiesChecker.check();
            return config;

        } catch (final YAMLException e) {
            throw new ConfigurationException("Invalid yaml: " + SystemUtils.LINE_SEPARATOR
                    +  " Error: " + e.getMessage(), false);
        } catch (final IOException e) {
            throw new ConfigurationException("Exception while loading configuration files.", e);
        }
    }


    // Everything below here is copied from YamlConfigurationLoader due to stupid scopes
    private static class PropertiesChecker extends PropertyUtils {
        private final Set<String> missingProperties = new HashSet<>();

        private final Set<String> nullProperties = new HashSet<>();

        public PropertiesChecker() {
            setSkipMissingProperties(true);
        }

        @Override
        public Property getProperty(Class<? extends Object> type, String name) throws IntrospectionException {
            final Property result = super.getProperty(type, name);

            if (result instanceof MissingProperty) {
                missingProperties.add(result.getName());
            }

            return new Property(result.getName(), result.getType()) {
                @Override
                public void set(Object object, Object value) throws Exception {
                    if (value == null && get(object) != null) {
                        nullProperties.add(getName());
                    }
                    result.set(object, value);
                }

                @Override
                public Class<?>[] getActualTypeArguments() {
                    return result.getActualTypeArguments();
                }

                @Override
                public Object get(Object object) {
                    return result.get(object);
                }
            };
        }

        public void check() throws ConfigurationException {
            if (!nullProperties.isEmpty()) {
                throw new ConfigurationException("Invalid yaml. Those properties " + nullProperties + " are not valid", false);
            }

            if (!missingProperties.isEmpty()) {
                throw new ConfigurationException("Invalid yaml. Please remove properties " + missingProperties + " from your cassandra.yaml", false);
            }
        }
    }

    static class CustomConstructor extends Constructor
    {
        CustomConstructor(Class<?> theRoot)
        {
            super(theRoot);

            TypeDescription seedDesc = new TypeDescription(ParameterizedClass.class);
            seedDesc.putMapPropertyType("parameters", String.class, String.class);
            addTypeDescription(seedDesc);
        }

        @Override
        protected List<Object> createDefaultList(int initSize)
        {
            return Lists.newCopyOnWriteArrayList();
        }

        @Override
        protected Map<Object, Object> createDefaultMap()
        {
            return Maps.newConcurrentMap();
        }

        @Override
        protected Set<Object> createDefaultSet(int initSize)
        {
            return Sets.newConcurrentHashSet();
        }

        @Override
        protected Set<Object> createDefaultSet()
        {
            return Sets.newConcurrentHashSet();
        }
    }
}
