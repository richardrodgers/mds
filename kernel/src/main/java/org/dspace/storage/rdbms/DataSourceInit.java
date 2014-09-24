/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.storage.rdbms;

import com.jolbox.bonecp.BoneCPDataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.dspace.core.ConfigurationManager;

public class DataSourceInit {
    private static Logger log = LoggerFactory.getLogger(DataSourceInit.class);

    private static DataSource dataSource = null;

    public static DataSource getDatasource() throws SQLException {
        if (dataSource != null) {
            return dataSource;
        }

        try {
            // Register basic JDBC driver
            Class driverClass = Class.forName(ConfigurationManager.getProperty("db.driver"));
            DriverManager.registerDriver((Driver)driverClass.newInstance());
            
            BoneCPDataSource bcpDS = new BoneCPDataSource();
            // access configuration
            bcpDS.setJdbcUrl(ConfigurationManager.getProperty("db.url"));
            bcpDS.setUsername(ConfigurationManager.getProperty("db.username"));
            bcpDS.setPassword(ConfigurationManager.getProperty("db.password"));
            // pool configuration
            bcpDS.setMaxConnectionsPerPartition(ConfigurationManager.getIntProperty("db.maxconnections", 30));
            int min = ConfigurationManager.getIntProperty("db.maxidle", -1);
            if (min > 0) {
                bcpDS.setMinConnectionsPerPartition(min);
            }
            bcpDS.setConnectionTimeoutInMs(ConfigurationManager.getIntProperty("db.maxwait", 5000));
            bcpDS.setPartitionCount(1);

            if (! ConfigurationManager.getBooleanProperty("db.statementpool",true))
            {
                bcpDS.setStatementsCacheSize(0);
            }
            
            String validationQuery = "SELECT 1";
            bcpDS.setConnectionTestStatement(validationQuery);
            log.debug("BoneCP dataSource initialized");
            dataSource = bcpDS;
            return dataSource;
        } catch (Exception e) {
            // Need to be able to catch other exceptions. Pretend they are
            // SQLExceptions, but do log
            log.warn("Exception initializing DB pool", e);
            throw new SQLException(e.toString(), e);
        }
    }
}
