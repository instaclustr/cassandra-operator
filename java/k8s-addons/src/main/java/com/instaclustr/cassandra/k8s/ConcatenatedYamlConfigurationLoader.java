package com.instaclustr.cassandra.k8s;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSortedMap;
import org.apache.cassandra.config.Config;
import org.apache.cassandra.config.ConfigurationLoader;
import org.apache.cassandra.exceptions.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
        final Iterable<String> configValues = Splitter.on(':').split(configProperty);

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

                // only load files
                .filter(path -> {
                    if (!Files.isRegularFile(path)) {
                        logger.warn("Configuration file \"{}\" is not a regular file and will not be loaded.", path);
                        return false;
                    }

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
            return yaml.loadAs(reader, Config.class);

        } catch (final IOException e) {
            throw new ConfigurationException("Exception while loading configuration files.", e);
        }
    }
}
