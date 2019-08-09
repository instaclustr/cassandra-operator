package com.instaclustr.picocli.typeconverter;

import java.nio.file.Path;
import java.nio.file.Paths;

import picocli.CommandLine;

public class PathTypeConverter implements CommandLine.ITypeConverter<Path> {
    @Override
    public Path convert(final String value) {
        try {
            return Paths.get(value);
        } catch (Exception e) {
            throw new CommandLine.TypeConversionException(String.format("\"%s\" can not be converted to path", value));
        }
    }
}
