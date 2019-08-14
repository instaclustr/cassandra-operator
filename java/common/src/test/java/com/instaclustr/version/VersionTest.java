package com.instaclustr.version;

import org.testng.annotations.Test;

public class VersionTest {

    @Test
    public void testVersion() {
        final Version version = new Version("1.2.3");
        final Version version2 = new Version(new String[]{"1", "2", "3"});
        final Version version3 = new Version((String) null);
        final Version version4 = new Version((String[]) null);
    }
}
