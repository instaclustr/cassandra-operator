package com.instaclustr.backup.task;

import com.instaclustr.backup.DirectoryBackupArguments;
import com.instaclustr.backup.uploader.FilesUploader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;
import java.util.LinkedList;
import java.util.concurrent.Callable;


public class DirectoryBackupTask implements Callable<Void> {

    private static final Logger logger = LoggerFactory.getLogger(DirectoryBackupTask.class);
    private final DirectoryBackupArguments arguments;
    private final Path backupRootKey;
    private final FilesUploader filesUploader;

    public DirectoryBackupTask(final String rootLabel,
                               final FilesUploader filesUploader,
                               final DirectoryBackupArguments arguments
    ) throws IOException {

        this.filesUploader = filesUploader;
        this.arguments = arguments;
        this.backupRootKey = Paths.get(rootLabel);
    }

    @Override
    public Void call() throws Exception {
        final Collection<ManifestEntry> manifest = new LinkedList<>(); // linked list to maintain order
        for (String source : arguments.sources) {
            logger.info("Backing up Source: {}", source);
            final Path srcPath = Paths.get(source);

            Files.walkFileTree(srcPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    logger.debug("Relative Path: {}", srcPath.getParent().relativize(file).toString());
                    manifest.add(new ManifestEntry(backupRootKey.resolve(srcPath.getParent().relativize(file)), file, ManifestEntry.Type.FILE));
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        logger.debug("{} files in manifest for Directory Backup.", manifest.size());
        filesUploader.uploadOrFreshenFiles(manifest, false);
        return null;
    }
}
