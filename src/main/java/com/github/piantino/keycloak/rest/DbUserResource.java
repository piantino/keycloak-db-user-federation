package com.github.piantino.keycloak.rest;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;

public interface DbUserResource {

	@POST
	@Path("{username}/sync")
	@Produces(MediaType.APPLICATION_JSON)
	public void sync(@PathParam("username") String username, @Context final HttpHeaders headers);

	@GET
	@Path("/metrics")
	@Produces(MediaType.APPLICATION_JSON)
	public String metrics(@Context final HttpHeaders headers);

}
