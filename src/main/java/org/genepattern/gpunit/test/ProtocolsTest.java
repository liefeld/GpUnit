package org.genepattern.gpunit.test;

import java.io.File;
import java.util.Collection;

import org.junit.runners.Parameterized.Parameters;

import org.genepattern.gpunit.BatchModuleTestObject;

/**
 * Run a batch of jobs, one for each protocol.
 * 
 * @author pcarr
 */
public class ProtocolsTest extends BatchModuleTest { 

    /**
     * @see BatchModuleUtil#data()
     */
    @Parameters(name="{0}")
    public static Collection<Object[]> data() {
        return BatchModuleUtil.data(new File("./tests/protocols"));
    }
    
    public ProtocolsTest(final int batchIdx, final BatchModuleTestObject testObj) {
        super(batchIdx, testObj);
    }

}
