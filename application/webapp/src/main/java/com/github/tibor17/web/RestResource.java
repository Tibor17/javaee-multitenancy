package com.github.tibor17.web;

import javax.annotation.security.RolesAllowed;
import javax.inject.Inject;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.net.URI;

import static javax.ws.rs.core.Response.created;

@Path("/cars")
public class RestResource {
    @Inject
    private MyService service;

    @POST
    @RolesAllowed("personal-cars")
    public Response doGetCars(@Context UriInfo uriInfo) {
        service.saveTenant1("RESTful");
        URI location = uriInfo.getBaseUriBuilder().path("1").build();
        return created(location).build();
    }
}
