package com.github.tibor17.holder;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;

import javax.enterprise.inject.Vetoed;

/**
 * Implementation for the hibernate tenant identifier resolver by schema.
 *
 */
@Vetoed
public class SchemaResolver implements CurrentTenantIdentifierResolver {

    @Override
    public String resolveCurrentTenantIdentifier() {
        return SchemaHolderSingleton.getInstance().getIdentifier();
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }

    public void setTenantIdentifier(String tenantIdentifier) {
        SchemaHolderSingleton.getInstance().setIdentifier(tenantIdentifier);
    }
}
