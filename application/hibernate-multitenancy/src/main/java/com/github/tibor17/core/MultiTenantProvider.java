package com.github.tibor17.core;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import com.github.tibor17.holder.SchemaHolderSingleton;
import org.hibernate.HibernateException;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.config.spi.ConfigurationService;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.hibernate.engine.jdbc.dialect.spi.DatabaseMetaDataDialectResolutionInfoAdapter;
import org.hibernate.engine.jdbc.dialect.spi.DialectResolutionInfo;
import org.hibernate.engine.jdbc.dialect.spi.DialectResolver;
import org.hibernate.service.UnknownUnwrapTypeException;
import org.hibernate.service.spi.ServiceRegistryAwareService;
import org.hibernate.service.spi.ServiceRegistryImplementor;

/**
 * Responsible for changing the schema before database interactions.
 * <br>
 * <br>
 * Hibernate doc: {@link see http://docs.jboss.org/hibernate/orm/4.3/devguide/en-US/html_single/#d5e4771}
 *
 */
public class MultiTenantProvider implements MultiTenantConnectionProvider, ServiceRegistryAwareService {

    private static final long serialVersionUID = 1L;

    private DataSource dataSource;

    private DialectResolver dialectResolver;

    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }

    @Override
    public void injectServices(ServiceRegistryImplementor serviceRegistry) {
        dialectResolver = serviceRegistry.getService(DialectResolver.class);

        /*try {
            final Context init = new InitialContext();
            dataSource = (DataSource) init.lookup("java:jboss/datasources/mt");
        } catch (final NamingException e) {
            throw new RuntimeException(e);
        }*/
        dataSource = (DataSource) serviceRegistry.getService(ConfigurationService.class)
                .getSettings()
                .get(AvailableSettings.DATASOURCE);
    }

    @SuppressWarnings("rawtypes")
    @Override
    public boolean isUnwrappableAs(Class clazz) {
        try {
            return getAnyConnection().isWrapperFor(clazz);
        } catch (SQLException e) {
            throw new UnknownUnwrapTypeException(clazz, e);
        }
        //return false;
    }

    @Override
    public <T> T unwrap(Class<T> clazz) {
        try {
            return getAnyConnection().unwrap(clazz);
        } catch (SQLException e) {
            throw new UnknownUnwrapTypeException(clazz, e);
        }
        //return null;
    }

    @Override
    public Connection getAnyConnection() throws SQLException {
        Connection c = dataSource.getConnection();
        System.out.println("getAnyConnection schema=" + c.getSchema());
        return c;
    }

    @Override
    public Connection getConnection(String tenantIdentifier) throws SQLException {
        Connection connection = getAnyConnection();
        DialectResolutionInfo dri = new DatabaseMetaDataDialectResolutionInfoAdapter(connection.getMetaData());
        Dialect dialect = dialectResolver.resolveDialect(dri);
        if (dialect.canCreateSchema()) {
            String connectionSchema = connection.getSchema();
            if (!tenantIdentifier.equalsIgnoreCase(connectionSchema)) {
                setSchema(tenantIdentifier, connection, dri);
            } else {
                String resolverSchema = SchemaHolderSingleton.getInstance().getIdentifier();
                if (!tenantIdentifier.equalsIgnoreCase(resolverSchema)) {
                    setSchema(resolverSchema, connection, dri);
                }
            }
        }
        /**/
        return connection;
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        //connection.setSchema("PUBLIC");
        /*try {
            Dialect dialect = dialectResolver.resolveDialect(new DatabaseMetaDataDialectResolutionInfoAdapter(connection.getMetaData()));
            connection.createStatement().execute("SET SCHEMA " + dialect.quote("public"));
        } catch (final SQLException e) {
            throw new HibernateException("Could not alter JDBC connection to specified schema [public]", e);
        }*/

        try {
            connection.createStatement().execute("SET SCHEMA 'public'");
        } catch (final SQLException e) {
            throw new HibernateException("Error trying to alter schema [public]", e);
        }
        // ERROR [org.hibernate.engine.jdbc.spi.SqlExceptionHelper] (default task-1) IJ031070: Transaction cannot proceed: STATUS_COMMITTED
        // connection.setSchema("public");
        connection.close();
    }

    @Override
    public void releaseConnection(String tenantIdentifier, Connection connection) throws SQLException {
        releaseAnyConnection(connection);
    }

    private void setSchema(String tenantIdentifier, Connection connection, DialectResolutionInfo dri)
            throws SQLException {
        /*try {
            Dialect dialect = dialectResolver.resolveDialect(dri);
            // dialect.quote(tenantIdentifier)
            connection.createStatement().execute("SET SCHEMA '" + tenantIdentifier + "'");
        } catch (SQLException e) {
            throw new HibernateException("Could not alter JDBC connection to specified schema ["
                    + tenantIdentifier
                    + "]", e);
        }*/
        connection.setSchema(tenantIdentifier);
    }
}
