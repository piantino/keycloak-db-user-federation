package com.github.piantino.keycloak.rest;

import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.services.resources.admin.permissions.AdminPermissionEvaluator;
import org.keycloak.storage.UserStorageProviderModel;
import org.keycloak.storage.user.SynchronizationResult;

import com.github.piantino.keycloak.DbUserProviderFactory;

import io.agroal.api.AgroalDataSourceMetrics;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

public class DbUserResource implements DbUserResourceApi {

	private KeycloakSession session;
	private AdminPermissionEvaluator auth;

	private DbUserProviderFactory factory;
	private final RealmModel realm;

	public DbUserResource(KeycloakSession session, AdminPermissionEvaluator auth) {
		this.session = session;
		this.auth = auth;
		this.factory = new DbUserProviderFactory();
		this.realm = session.getContext().getRealm();
	}

	@POST
	@Path("{username}/sync")
	@Produces(MediaType.APPLICATION_JSON)
	@Override
	public void sync(@PathParam("username") String username) {
		this.auth.users().requireManage();

		UserStorageProviderModel model = DbUserProviderFactory.getModel(this.realm);
		KeycloakSessionFactory sessionFactory = session.getKeycloakSessionFactory();

		SynchronizationResult result = this.factory.syncUsername(username, sessionFactory, this.realm.getId(), model);
		if (result.getAdded() == 0 && result.getUpdated() == 0) {
			throw new NotFoundException("Username " + username + " not found");
		}
	}

	@GET
	@Path("/metrics")
	@Produces(MediaType.APPLICATION_JSON)
	@Override
	public String metrics() {
		this.auth.requireAnyAdminRole();

		AgroalDataSourceMetrics metrics = DbUserProviderFactory.getDataSourceMetrics(this.realm);
		return metrics.toString();
	}
}
