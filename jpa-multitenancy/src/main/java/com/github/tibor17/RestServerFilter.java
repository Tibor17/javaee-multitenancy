package com.github.tibor17;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.ext.Provider;
import java.io.IOException;

@Provider
public class RestServerFilter implements ContainerRequestFilter {
    private SchemaResolver resolver = new SchemaResolver();

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String tenant = requestContext.getHeaders().get("X-TENANT").get(0);
        resolver.setTenantIdentifier(tenant);
    }
}
