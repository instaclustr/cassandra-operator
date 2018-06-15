package com.instaclustr;

import com.google.gson.Gson;
import org.yaml.snakeyaml.Yaml;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class YAML2JSON {

    public static void main(String[] args) throws IOException {
        final Path inputFile = Paths.get("/Users/adam/Projects/cassandra-operator/src/main/resources/com/instaclustr/cluster-crd.yaml");
        final Path outputFile = Paths.get("/Users/adam/Projects/cassandra-operator/src/main/resources/com/instaclustr/cassandra-crd.json");

        try (final BufferedReader reader = Files.newBufferedReader(inputFile);
             final BufferedWriter writer = Files.newBufferedWriter(outputFile)) {
            final Yaml yaml = new Yaml();

            final Object object = yaml.load(reader);

            final Gson gson = new Gson();

            gson.toJson(object, writer);
        }
    }
}
