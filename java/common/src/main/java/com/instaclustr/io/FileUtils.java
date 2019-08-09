package com.instaclustr.io;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumSet;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileUtils {
    private static final Logger logger = LoggerFactory.getLogger(FileUtils.class);

//    public static boolean writeIfChanged(final InputStream content, final Path writePath) throws IOException {
    // import com.amazonaws.util.IOUtils;
//        return writeIfChanged(ByteBuffer.wrap(IOUtils.toByteArray(content)), writePath);
//    }

    public static boolean writeIfChanged(final ByteBuffer content, final Path writePath) throws IOException {
        boolean changed = false;

        try {
            final MessageDigest existingDigest = MessageDigest.getInstance("SHA-256");
            if (Files.exists(writePath)) {
                existingDigest.update(Files.readAllBytes(writePath));
            }

            final MessageDigest newDigest = MessageDigest.getInstance("SHA-256");
            newDigest.update(content.asReadOnlyBuffer());

            if (!MessageDigest.isEqual(existingDigest.digest(), newDigest.digest())) {
                writeTempAndReplace(content, writePath);
                changed = true;
            }

        } catch (final NoSuchAlgorithmException e) {
            throw new IOException("Failed to calculate MessageDigest.", e);
        }

        return changed;
    }

    private static void writeTempAndReplace(final ByteBuffer content, final Path writePath) throws IOException {
        final Path tempPath = Files.createTempFile(writePath.getFileName().toString(), null);
        try {
            try (WritableByteChannel channel = Files.newByteChannel(tempPath, EnumSet.of(StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE, StandardOpenOption.WRITE))) {
                channel.write(content);
            }
            Files.move(tempPath, writePath, StandardCopyOption.REPLACE_EXISTING);
            logger.info("Replaced {}.", writePath);
            chownInheritParent(writePath);
        } catch (final Exception e) {
            try {
                Files.deleteIfExists(tempPath);
            } catch (final IOException ex) {
                logger.warn("Failed to delete temporary file {}.", tempPath.getFileName(), ex);
            }
            throw e;
        }
    }

    private static void chownInheritParent(final Path childPath) throws IOException {
        final PosixFileAttributeView child = Files.getFileAttributeView(childPath, PosixFileAttributeView.class);
        final PosixFileAttributeView parent = Files.getFileAttributeView(childPath.getParent(), PosixFileAttributeView.class);

        child.setOwner(parent.getOwner());
        child.setGroup(parent.readAttributes().group());
    }

    public static void cleanDirectory(final File directory) {
        File[] fileList = directory.listFiles();

        if (fileList == null) {
            return;
        }

        for (File file : fileList) {
            if (file.isDirectory()) cleanDirectory(file);

            if (!file.delete()) {
                logger.warn("Failed to delete {}", file.getAbsolutePath());
            }
        }
    }

    public static void writeProperties(final PrintWriter writer, final Properties properties) {
        for (final Map.Entry<Object, Object> property : properties.entrySet()) {
            // only allow a-zA-Z0-9_ for shell variable names, uppercase
            final String key = property.getKey().toString().toUpperCase().replaceAll("[^\\p{Alnum}]", "_");
            final String heredoc = RandomStringUtils.randomAlphabetic(8);

            // use a heredoc to escape funky values (if any)
            writer.format("read -r %s <<'%s'%n%s%n%2$s%n", key, heredoc, property.getValue());
        }
    }
}
