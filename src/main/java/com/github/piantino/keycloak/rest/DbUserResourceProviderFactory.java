package com.github.piantino.keycloak.rest;

import org.keycloak.Config.Scope;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.services.resource.RealmResourceProvider;
import org.keycloak.services.resource.RealmResourceProviderFactory;

public class DbUserResourceProviderFactory implements RealmResourceProviderFactory {

    @Override
    public String getId() {
        return "db-user";
    }

    @Override
    public RealmResourceProvider create(KeycloakSession session) {
        return new DbUserResourceProvider(session);
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

}
