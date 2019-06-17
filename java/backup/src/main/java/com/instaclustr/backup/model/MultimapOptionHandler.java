package com.instaclustr.backup.model;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.apache.commons.lang3.StringUtils;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.OneArgumentOptionHandler;
import org.kohsuke.args4j.spi.Setter;

public class MultimapOptionHandler extends OneArgumentOptionHandler<Multimap<String, String>> {

    public MultimapOptionHandler(final CmdLineParser parser, final OptionDef option,
                                 final Setter<? super Multimap<String, String>> setter) {
        super(parser, option, setter);
    }

    @Override
    protected Multimap<String, String> parse(String argument) throws NumberFormatException, CmdLineException {

        Multimap<String, String> keyspaceTableSubset = HashMultimap.create();

        for (String rawPair : argument.split(",")) {
            String[] pair = StringUtils.split(rawPair.trim(), '.');
            if (pair.length != 2)
                throw new CmdLineException(this.owner, "Keyspace-tables requires a comma-separate list of keyspace-table pairs, e.g., 'test.test1,test2.test1'");

            keyspaceTableSubset.put(pair[0], pair[1]);
        }


        return keyspaceTableSubset;
    }

    @Override
    public String getDefaultMetaVariable() {
        return "OPTIONS";
    }
}
