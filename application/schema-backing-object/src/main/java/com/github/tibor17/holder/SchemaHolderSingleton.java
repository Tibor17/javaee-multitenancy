package com.github.tibor17.holder;

import javax.enterprise.inject.Vetoed;
import javax.enterprise.inject.spi.CDI;
import javax.inject.Provider;

@Vetoed
public final class SchemaHolderSingleton {
    private static final ThreadLocal<String> THREAD_TENANT = new ThreadLocal<>();

    private SchemaHolderSingleton() {
    }

    public static SchemaHolderSingleton getInstance() {
        return LazyHolder.INSTANCE;
    }

    public String getIdentifier() {
        return THREAD_TENANT.get();
    }

    public void setIdentifier(String identifier) {
        if (identifier == null) {
            THREAD_TENANT.remove();
        } else {
            THREAD_TENANT.set(identifier);
        }
    }

    public static final class LazyHolder {
        public static final SchemaHolderSingleton INSTANCE = new SchemaHolderSingleton();

        private LazyHolder() {
        }
    }
}
