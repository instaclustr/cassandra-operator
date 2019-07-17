package com.instaclustr.cassandra.backup.impl;

import static java.lang.String.format;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import javax.validation.Constraint;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import javax.validation.Payload;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.google.inject.Inject;
import com.instaclustr.cassandra.backup.guice.StorageProviders;
import picocli.CommandLine;

public class StorageLocation {
    private static final Pattern filePattern = Pattern.compile("(.*)://(.*)/(.*)/(.*)/(.*)");
    private static final Pattern cloudPattern = Pattern.compile("(.*)://(.*)/(.*)/(.*)");

    public String rawLocation;
    public String storageProvider;
    public String bucket;
    public String clusterId;
    public String nodeId;
    public Path fileBackupDirectory;
    public boolean cloudLocation;

    public StorageLocation(final String rawLocation) {
        this.rawLocation = rawLocation;

        if (this.rawLocation.startsWith("file")) {
            initializeFileBackupLocation(this.rawLocation);
        } else {
            cloudLocation = true;
            initializeCloudBackupLocation(this.rawLocation);
        }
    }

    private void initializeFileBackupLocation(final String backupLocation) {
        final Matcher matcher = filePattern.matcher(backupLocation);

        if (!matcher.matches()) {
            return;
        }

        this.rawLocation = matcher.group();
        this.storageProvider = matcher.group(1);
        this.fileBackupDirectory = Paths.get(matcher.group(2));
        this.bucket = matcher.group(3);
        this.clusterId = matcher.group(4);
        this.nodeId = matcher.group(5);
    }

    private void initializeCloudBackupLocation(final String backupLocation) {
        final Matcher matcher = cloudPattern.matcher(backupLocation);

        if (!matcher.matches()) {
            return;
        }

        this.rawLocation = matcher.group();
        this.storageProvider = matcher.group(1);
        this.bucket = matcher.group(2);
        this.clusterId = matcher.group(3);
        this.nodeId = matcher.group(4);
    }

    public void validate() throws IllegalStateException {
        if (cloudLocation) {
            if (rawLocation == null || storageProvider == null || bucket == null || clusterId == null || nodeId == null) {
                throw new IllegalStateException(format("Backup location %s is not in form protocol://bucketName/clusterId/nodeId",
                                                       rawLocation));
            }
        } else if (rawLocation == null || storageProvider == null || bucket == null || clusterId == null || nodeId == null || fileBackupDirectory == null) {
            throw new IllegalStateException(format("Backup location %s is not in form file:///some/backup/path/bucketName/clusterId/nodeId",
                                                   rawLocation));
        }
    }

    @Override
    public String toString() {
        return rawLocation;
    }

    @Target({TYPE, PARAMETER, FIELD})
    @Retention(RUNTIME)
    @Constraint(validatedBy = ValidBackupLocation.BackupLocationValidator.class)
    public @interface ValidBackupLocation {
        String message() default "{com.instaclustr.cassandra.backup.impl.StorageLocation.BackupLocationValidator.message}";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};

        class BackupLocationValidator implements ConstraintValidator<ValidBackupLocation, StorageLocation> {
            private final Set<String> storageProviders;

            @Inject
            public BackupLocationValidator(final @StorageProviders Set<String> storageProviders) {
                this.storageProviders = storageProviders;
            }

            @Override
            public boolean isValid(final StorageLocation value, final ConstraintValidatorContext context) {

                if (value == null) {
                    return true;
                }

                context.disableDefaultConstraintViolation();

                try {
                    value.validate();
                } catch (Exception ex) {
                    context.buildConstraintViolationWithTemplate(format("Invalid backup location: %s",
                                                                        ex.getLocalizedMessage())).addConstraintViolation();
                    return false;
                }

                if (!storageProviders.contains(value.storageProvider)) {
                    context.buildConstraintViolationWithTemplate(format("Available providers: %s",
                                                                        Arrays.toString(storageProviders.toArray()))).addConstraintViolation();

                    return false;
                }

                return true;
            }
        }
    }

    public static class StorageLocationTypeConverter implements CommandLine.ITypeConverter<StorageLocation> {
        @Override
        public StorageLocation convert(final String value) throws Exception {
            if (value == null) {
                return null;
            }

            try {
                return new StorageLocation(value);
            } catch (final Exception ex) {
                throw new CommandLine.TypeConversionException(format("Invalid value of StorageLocation '%s', reason: %s",
                                                                     value,
                                                                     ex.getLocalizedMessage()));
            }
        }
    }

    public static class StorageLocationSerializer extends StdSerializer<StorageLocation> {

        protected StorageLocationSerializer(final Class<StorageLocation> t) {
            super(t);
        }

        @Override
        public void serialize(final StorageLocation value, final JsonGenerator gen, final SerializerProvider provider) throws IOException {
            if (value != null) {
                gen.writeString(value.toString());
            }
        }
    }

    public static class StorageLocationDeserializer extends StdDeserializer<StorageLocation> {

        public StorageLocationDeserializer() {
            super(StorageLocation.class);
        }

        @Override
        public StorageLocation deserialize(final JsonParser p, final DeserializationContext ctxt) throws IOException {
            final String valueAsString = p.getValueAsString();

            if (valueAsString == null) {
                return null;
            }

            try {
                return new StorageLocation(valueAsString);
            } catch (final Exception ex) {
                throw new InvalidFormatException(p, "Invalid StorageLocation.", valueAsString, StorageLocation.class);
            }
        }
    }
}
