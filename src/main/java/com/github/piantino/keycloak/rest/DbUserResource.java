package com.github.piantino.keycloak.rest;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.keycloak.storage.user.SynchronizationResult;

public interface DbUserResource {

	@POST
	@Path("{username}/sync")
	@Produces(MediaType.TEXT_PLAIN)
	public SynchronizationResult sync(@PathParam("username") String username);

}
