package com.github.piantino.keycloak.rest;

import org.keycloak.Config.Scope;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.services.resources.admin.AdminEventBuilder;
import org.keycloak.services.resources.admin.ext.AdminRealmResourceProvider;
import org.keycloak.services.resources.admin.ext.AdminRealmResourceProviderFactory;
import org.keycloak.services.resources.admin.permissions.AdminPermissionEvaluator;

public class DbUserResourceProviderFactory implements AdminRealmResourceProviderFactory, AdminRealmResourceProvider {

    @Override
    public String getId() {
        return "db-user";
    }

    @Override
    public AdminRealmResourceProvider create(KeycloakSession session) {
        return this;
    }

    @Override
    public void init(Scope config) {
        // Do nothing
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // Do nothing
    }

    @Override
    public void close() {
        // Do nothing
    }

      @Override
    public Object getResource(KeycloakSession session, RealmModel realm, AdminPermissionEvaluator auth, AdminEventBuilder adminEvent) {
        return new DbUserResource(session, auth);
    }

}
