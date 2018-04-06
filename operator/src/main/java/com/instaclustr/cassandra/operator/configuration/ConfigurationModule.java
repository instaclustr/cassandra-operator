package com.instaclustr.cassandra.operator.configuration;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.base.StandardSystemProperty;
import com.google.common.collect.Iterables;
import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.matcher.Matchers;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.Properties;
import java.util.regex.Pattern;

public class ConfigurationModule extends AbstractModule {
//    public static Properties getConfigurationProperties() {
//        return new Properties() {{
//            final Iterable<Path> paths = Iterables.transform(
//                    Splitter.on(Pattern.compile("(?<!\\\\):"))
//                            .omitEmptyStrings()
//                            .split(Optional.fromNullable(System.getProperty("ic.configurationFile")).or("configuration.properties")),
//                    new Function<String, Path>() {
//                        @Nullable
//                        @Override
//                        public Path apply(final String input) {
//                            return Paths.get(input);
//                        }
//                    }
//            );
//
//            for (final Path path: paths) {
//                try (final BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
//                    this.load(reader);
//
//                } catch (final IOException e) {
//                    logger.warn("Failed to load configuration file \"{}\".", path, e);
//                }
//            }
//        }};
//    }

    @Override
    protected void configure() {
//
//
//        final Properties configurationProperties = getConfigurationProperties();
//
//        // configuration file properties
//        for (final Enumeration<?> e = configurationProperties.propertyNames(); e.hasMoreElements(); ) {
//            final String propertyName = (String) e.nextElement();
//            final String value = configurationProperties.getProperty(propertyName);
//
//            bindConstant().annotatedWith(new ConfigurationPropertyImpl(propertyName)).to(value);
//        }
//
//        // standard system properties
//        for (final StandardSystemProperty property: StandardSystemProperty.values()) {
//            final String value = property.value();
//
//            if (value != null)
//                bindConstant().annotatedWith(new SystemPropertyImpl(property)).to(value);
//        }
//
//        // type converters
//        convertToTypes(Matchers.only(TypeLiteral.get(URI.class)), new URITypeConverter());
//        convertToTypes(Matchers.only(TypeLiteral.get(InetAddress.class)), new InetAddressTypeConverter());
//        convertToTypes(Matchers.only(TypeLiteral.get(Path.class)), new PathTypeConverter());
//        convertToTypes(Matchers.only(TypeLiteral.get(UUID.class)), new UUIDTypeConverter());
//        convertToTypes(Matchers.only(TypeLiteral.get(JMXServiceURL.class)), new JMXServiceURLTypeConverter());
    }
}
