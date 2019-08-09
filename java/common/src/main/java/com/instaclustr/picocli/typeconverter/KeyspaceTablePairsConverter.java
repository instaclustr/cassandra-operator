package com.instaclustr.picocli.typeconverter;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.apache.commons.lang3.StringUtils;
import picocli.CommandLine;

public class KeyspaceTablePairsConverter implements CommandLine.ITypeConverter<Multimap<String, String>> {
    @Override
    public Multimap<String, String> convert(final String value) throws Exception {
        Multimap<String, String> keyspaceTableSubset = HashMultimap.create();

        for (String rawPair : value.split(",")) {
            String[] pair = StringUtils.split(rawPair.trim(), '.');
            if (pair.length != 2)
                throw new CommandLine.TypeConversionException("Keyspace-tables requires a comma-separate list of keyspace-table pairs, e.g., 'test.test1,test2.test1'");

            keyspaceTableSubset.put(pair[0], pair[1]);
        }


        return keyspaceTableSubset;
    }
}
