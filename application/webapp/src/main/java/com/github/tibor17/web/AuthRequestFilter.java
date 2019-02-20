package com.github.tibor17.web;

import com.github.tibor17.holder.SchemaHolderSingleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.ext.Provider;
import java.io.IOException;

@Provider
public class AuthRequestFilter implements ContainerRequestFilter {
    private static final Logger LOG = LoggerFactory.getLogger(AuthRequestFilter.class);

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String basicAuthUserName = requestContext.getSecurityContext().getUserPrincipal().getName();
        LOG.info("basicAuthUserName={}", basicAuthUserName);
        SchemaHolderSingleton.getInstance().setIdentifier(basicAuthUserName);
    }
}
