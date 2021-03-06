package tests.eu.qualimaster.dataManagement;

import java.util.Properties;

import org.junit.Assert;
import org.junit.Test;

import eu.qualimaster.dataManagement.DataManagementConfiguration;
import tests.eu.qualimaster.ConfigurationTests;

/**
 * Tests the configuration.
 * 
 * @author Holger Eichelberger
 */
public class DataManagementConfigurationTests extends ConfigurationTests {

    @Override
    protected void testDirect() {
        super.testDirect();
        Assert.assertEquals(DataManagementConfiguration.DEFAULT_URL_HDFS, DataManagementConfiguration.getHdfsUrl());
        Assert.assertEquals(DataManagementConfiguration.DEFAULT_PATH_DFS, DataManagementConfiguration.getDfsPath());
    }

    @Override
    protected void testAfterReplay() {
        super.testAfterReplay();
    }
    
    @Override
    protected void buildProperties(Properties prop) {
        super.buildProperties(prop);
    }
    
    @Override
    protected void testViaProperties() {
        super.testViaProperties();
    }

    @Override
    @Test
    public void configurationTest() {
        super.configurationTest();
    }
    
}
