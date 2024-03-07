package com.github.piantino.keycloak.rest;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

public interface DbUserResourceApi {

	@POST
	@Path("{username}/sync")
	@Produces(MediaType.APPLICATION_JSON)
	public void sync(@PathParam("username") String username);

	@GET
	@Path("/metrics")
	@Produces(MediaType.APPLICATION_JSON)
	public String metrics();

}
