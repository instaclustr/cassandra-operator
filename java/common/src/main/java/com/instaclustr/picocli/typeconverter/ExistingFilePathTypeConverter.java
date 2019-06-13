package com.instaclustr.picocli.typeconverter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import picocli.CommandLine;

/***
 * A {@link CommandLine.ITypeConverter} for {@link java.nio.file.Path}s that must exist and must be regular files.
 */
public class ExistingFilePathTypeConverter implements CommandLine.ITypeConverter<Path> {
    @Override
    public Path convert(final String value) {
        if ("".equals(value))
            return null;

        final Path path = Paths.get(value);

        if (!Files.exists(path))
            throw new CommandLine.TypeConversionException(String.format("File \"%s\" does not exist", path));

        if (!Files.isRegularFile(path))
            throw new CommandLine.TypeConversionException(String.format("\"%s\" is not a regular file", path));

        return path;
    }
}
