package com.instaclustr.backup.model;

import javax.management.remote.JMXServiceURL;
import java.net.MalformedURLException;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.Messages;
import org.kohsuke.args4j.spi.OneArgumentOptionHandler;
import org.kohsuke.args4j.spi.Setter;

public class JMXUrlOptionHandler extends OneArgumentOptionHandler<JMXServiceURL> {
    public JMXUrlOptionHandler(CmdLineParser parser, OptionDef option, Setter<? super JMXServiceURL> setter) {
        super(parser, option, setter);
    }

    protected JMXServiceURL parse(String argument) throws CmdLineException {
        try {
            return new JMXServiceURL(argument);
        } catch (MalformedURLException e) {
            throw new CmdLineException(this.owner, Messages.ILLEGAL_PATTERN, new String[]{argument});
        }
    }
}

