package com.github.tibor17;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;

/**
 * Implementation for the hibernate tenant identifier resolver by schema.
 *
 */
public class SchemaResolver implements CurrentTenantIdentifierResolver {
    private static final InheritableThreadLocal<String> THREAD_LOCAL = new InheritableThreadLocal<>();

    @Override
    public String resolveCurrentTenantIdentifier() {
        if (THREAD_LOCAL.get() == null) {
            setTenantIdentifier("PUBLIC");
        }
        return THREAD_LOCAL.get();
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }

    public void setTenantIdentifier(String tenantIdentifier) {
        THREAD_LOCAL.set(tenantIdentifier);
    }
}
