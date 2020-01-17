package com.instaclustr.cassandra.k8s;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.google.common.base.Splitter;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import org.apache.cassandra.config.Config;
import org.apache.cassandra.config.ConfigurationLoader;
import org.apache.cassandra.config.TransparentDataEncryptionOptions;
import org.apache.cassandra.exceptions.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * A ConfigurationLoader that reads from one or more specified YAML-formatted configuration files, including files
 * contained within any specified directories. The contents of these configuration files will be concatenated
 * together and then read as if they were a single file. Files are concatenated in the order specified, and files
 * in any specified directories will be concatenated in lexicographical order.
 */
public class ConcatenatedYamlConfigurationLoader implements ConfigurationLoader {
    private static final Logger logger = LoggerFactory.getLogger(ConcatenatedYamlConfigurationLoader.class);

    private static final PathMatcher YAML_PATH_MATCHER = FileSystems.getDefault().getPathMatcher("glob:**/*.{yaml,yml}");

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


    static final class ConfigSupplier implements Supplier<Config> {
        @Override
        public Config get() {
            final String configProperty = System.getProperty("cassandra.config");
            logger.info("Loading config from {}", configProperty);

            final Iterable<String> configValues = Splitter.on(':').split(configProperty);

            final List<BufferedReader> readers = StreamSupport.stream(configValues.spliterator(), false)
                    .map(Paths::get)

                    // recurse into any specified directories and load any config files within
                    .flatMap(path -> {
                        if (!Files.exists(path)) {
                            logger.warn("Specified configuration file/directory {} does not exist.", path);
                            return Stream.empty();
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
                        if (!YAML_PATH_MATCHER.matches(path)) {
                            logger.warn("Configuration file \"{}\" is not a YAML file and will not be loaded.", path);
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
                final Yaml yaml = new Yaml();

                final Config config = yaml.loadAs(reader, Config.class);

                return config == null ? new Config() : config;

            } catch (final IOException | YAMLException e) {
                throw new ConfigurationException("Exception while loading configuration files.", e);
            }
        }
    }

    private static final Supplier<Config> CONFIG_SUPPLIER = Suppliers.memoize(new ConfigSupplier());

    @Override
    public Config loadConfig() throws ConfigurationException {
        Config config = CONFIG_SUPPLIER.get();

        try {
            Files.write(Paths.get("/var/lib/cassandra/cassandra-config.yaml"), log(config).getBytes(), CREATE, TRUNCATE_EXISTING);
        } catch (IOException ex) {
            logger.error("Unable to write cassandra configuration to /var/lib/cassandra-config.yaml", ex);
        }

        return config;
    }

    private static final List<String> SENSITIVE_KEYS = new ArrayList<String>() {{
        add("client_encryption_options");
        add("server_encryption_options");
    }};

    // taken and tweaked from Config in Cassandra
    public String log(Config config)
    {
        Map<String, String> configMap = new TreeMap<>();

        for (Field field : Config.class.getFields())  {
            // ignore the constants
            if (Modifier.isFinal(field.getModifiers()))
                continue;

            String name = field.getName();

            if (SENSITIVE_KEYS.contains(name)) {
                configMap.put(name, "<REDACTED>");
                continue;
            }

            String value;

            try {
                // Field.get() can throw NPE if the value of the field is null

                Object fieldValue = field.get(config);

                if (name.equals("data_file_directories")) {
                    if (fieldValue == null) {
                        value = new ArrayList<String>().toString();
                    } else {
                        value = Arrays.toString((String[])fieldValue);
                    }
                } else {
                    if (fieldValue == null) {
                        value = "null";
                    } else if (fieldValue instanceof TransparentDataEncryptionOptions) {
                        value = buildTransparentDataEncryptionOptions((TransparentDataEncryptionOptions) fieldValue);
                    } else {
                        value = fieldValue.toString();
                    }
                }
            } catch (NullPointerException | IllegalAccessException npe) {
                value = "null";
            }

            configMap.put(name, value);
        }

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);

        Yaml yaml = new Yaml(options);

        return yaml.dump(configMap);
    }

    private String buildTransparentDataEncryptionOptions(TransparentDataEncryptionOptions options) {
        return new HashMap<String, Object>(){{
            put("cipher", options.cipher);
            put("key_alias", options.key_alias);
            put("chunk_length_kb", options.chunk_length_kb);
            put("enabled", options.enabled);
            put("iv_length", options.iv_length);
        }}.toString();
    }
}
