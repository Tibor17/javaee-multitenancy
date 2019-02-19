package com.github.tibor17.commons;

import com.github.tibor17.holder.SchemaHolderSingleton;

import javax.enterprise.context.Destroyed;
import javax.enterprise.context.Initialized;
import javax.enterprise.context.RequestScoped;
import javax.enterprise.event.Observes;

public class SchemaHolderLifecycle {
    public void requestContextStarted(@Observes @Initialized(RequestScoped.class) Object bean) {
        SchemaHolderSingleton.getInstance().setIdentifier("PUBLIC");
    }

    public void requestContextFinished(@Observes @Destroyed(RequestScoped.class) Object bean) {
        SchemaHolderSingleton.getInstance().setIdentifier("PUBLIC");
    }
}
