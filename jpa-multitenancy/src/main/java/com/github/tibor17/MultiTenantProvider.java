package com.github.tibor17;

import java.sql.Connection;
import java.sql.SQLException;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

import org.hibernate.HibernateException;
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.hibernate.engine.jdbc.dialect.spi.DatabaseMetaDataDialectResolutionInfoAdapter;
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

    private static final SchemaResolver RESOLVER = new SchemaResolver();

    private DataSource dataSource;

    private DialectResolver dialectResolver;

    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }

    @Override
    public void injectServices(ServiceRegistryImplementor serviceRegistry) {
        dialectResolver = serviceRegistry.getService(DialectResolver.class);
        try {
            final Context init = new InitialContext();
            dataSource = (DataSource) init.lookup("java:comp/env/jdbc/JavaEEMTDS");
        } catch (final NamingException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("rawtypes")
    @Override
    public boolean isUnwrappableAs(Class clazz) {
        try {
            return getAnyConnection().isWrapperFor(clazz);
        } catch (SQLException e) {
            throw new UnknownUnwrapTypeException(clazz, e);
        }
    }

    @Override
    public <T> T unwrap(Class<T> clazz) {
        try {
            return getAnyConnection().unwrap(clazz);
        } catch (SQLException e) {
            throw new UnknownUnwrapTypeException(clazz, e);
        }
    }

    @Override
    public Connection getAnyConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public Connection getConnection(String tenantIdentifier) throws SQLException {
        Connection connection = getAnyConnection();
        Dialect dialect = dialectResolver.resolveDialect(new DatabaseMetaDataDialectResolutionInfoAdapter(connection.getMetaData()));
        String connectionSchema = connection.getSchema();
        String resolverSchema = RESOLVER.resolveCurrentTenantIdentifier();
        if (dialect.canCreateSchema()) {
            if (!tenantIdentifier.equalsIgnoreCase(connectionSchema)) {
                connection.setSchema(tenantIdentifier);
            } else if (!tenantIdentifier.equalsIgnoreCase(resolverSchema)) {
                connection.setSchema(resolverSchema);
            }
        }
        /*try {
            connection.createStatement().execute("SET SCHEMA " + dialect.quote(tenantIdentifier));
        } catch (SQLException e) {
            throw new HibernateException("Could not alter JDBC connection to specified schema [" + tenantIdentifier + "]", e);
        }*/
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
        connection.close();
    }

    @Override
    public void releaseConnection(String tenantIdentifier, Connection connection) throws SQLException {
        releaseAnyConnection(connection);
    }
}
