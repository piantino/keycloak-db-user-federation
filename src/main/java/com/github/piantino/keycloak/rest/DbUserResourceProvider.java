package com.github.piantino.keycloak.rest;

import java.net.URI;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.ws.rs.ForbiddenException;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;

import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.services.resource.RealmResourceProvider;
import org.keycloak.services.resources.admin.AdminAuth;
import org.keycloak.services.resources.admin.AdminRoot;
import org.keycloak.storage.UserStorageProviderModel;
import org.keycloak.storage.user.SynchronizationResult;

import com.github.piantino.keycloak.DbUserProviderFactory;
import com.github.piantino.keycloak.exception.DbUserProviderException;

// TODO: Use AdminResourceProvider in keycloak 19 instead AdminRoot
public class DbUserResourceProvider extends AdminRoot implements RealmResourceProvider, DbUserResource {

	private static final Pattern REALM_PATTERN = Pattern.compile("^/realms\\/([^\\/]+)\\/.+$");

	private DbUserProviderFactory factory;

	public DbUserResourceProvider(KeycloakSession session) {
		super();
		this.session = session;
		this.factory = new DbUserProviderFactory();
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
	public void sync(@PathParam("username") String username, @Context HttpHeaders headers) {
		checkAuth(headers);

		RealmModel realm = session.realms().getRealmByName(getRealmName());
		UserStorageProviderModel model = getModel(realm);
		KeycloakSessionFactory sessionFactory = session.getKeycloakSessionFactory();

		SynchronizationResult result = this.factory.syncUsername(username, sessionFactory, realm.getId(), model);
		if (result.getAdded() == 0 && result.getUpdated() == 0) {
			throw new NotFoundException("Username " + username+ " not found");
		}
	}

	private void checkAuth(HttpHeaders headers) {
		AdminAuth auth = authenticateRealmAdminRequest(headers);

		if (auth == null) {
			throw new NotAuthorizedException("Bearer");
		}

		RoleModel adminRole = auth.getRealm().getRole("admin");

		if (!auth.getUser().hasRole(adminRole)) {
			throw new ForbiddenException("User is not admin");
		}
	}

	private String getRealmName() {
		URI uri = session.getContext().getUri().getAbsolutePath();
		Matcher matcher = REALM_PATTERN.matcher(uri.getPath());
		if (!matcher.find()) {
			throw new DbUserProviderException("Invalid URL: " + uri);
		}
		return matcher.group(1);
	}

	private UserStorageProviderModel getModel(RealmModel realm) {
		return realm.getUserStorageProvidersStream()
				.filter(fedProvider -> Objects.equals(fedProvider.getProviderId(), DbUserProviderFactory.PROVIDER_ID))
				.findFirst()
				.orElseThrow(() -> new DbUserProviderException("db-user-provided not configured"));
	}

}
