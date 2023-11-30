package com.github.piantino.keycloak.rest;

import java.util.Objects;

import javax.ws.rs.ForbiddenException;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.PathParam;

import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.services.managers.AppAuthManager;
import org.keycloak.services.managers.AuthenticationManager.AuthResult;
import org.keycloak.services.resource.RealmResourceProvider;
import org.keycloak.storage.UserStorageProviderModel;
import org.keycloak.storage.user.SynchronizationResult;

import com.github.piantino.keycloak.DbUserProviderFactory;
import com.github.piantino.keycloak.exception.DbUserProviderException;

public class DbUserResourceProvider implements RealmResourceProvider, DbUserResource {

	private final KeycloakSession session;

	public DbUserResourceProvider(KeycloakSession session) {
		this.session = session;
	}

	@Override
	public Object getResource() {
		return this;
	}

	@Override
	public void close() {
		// Do nothing
	}

	@Override
	public SynchronizationResult sync(@PathParam("username") String username) {
		checkAuth();

		RealmModel realm = session.getContext().getRealm();
		DbUserProviderFactory factory = new DbUserProviderFactory();
		UserStorageProviderModel model = getModel(realm, factory);
		KeycloakSessionFactory sessionFactory = session.getKeycloakSessionFactory();

		return factory.syncUsername(username, sessionFactory, realm.getId(), model);
	}

	private AuthResult checkAuth() {
		AuthResult auth = new AppAuthManager.BearerTokenAuthenticator(session).authenticate();
		if (auth == null) {
			throw new NotAuthorizedException("Bearer");
		} else if (auth.getToken().getIssuedFor() == null || !auth.getToken().getIssuedFor().equals("admin-cli")) {
			throw new ForbiddenException("Token is not properly issued for admin-cli");
		}
		return auth;
	}

	private UserStorageProviderModel getModel(RealmModel realm, DbUserProviderFactory factory) {
		return realm.getUserStorageProvidersStream()
				.filter(fedProvider -> Objects.equals(fedProvider.getProviderId(), factory.getId()))
				.findFirst()
				.orElseThrow(() -> new DbUserProviderException("db-user-provided not configured"));
	}

}
