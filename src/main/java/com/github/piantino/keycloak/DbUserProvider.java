package com.github.piantino.keycloak;

import java.sql.Timestamp;
import java.util.Map;

import org.jboss.logging.Logger;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.storage.UserStorageProvider;
import org.keycloak.storage.user.ImportedUserValidation;

public class DbUserProvider implements UserStorageProvider, ImportedUserValidation {

    protected static final Logger LOGGER = Logger.getLogger(DbUserProvider.class);

    private static final String USERNAME = "username";
    private static final String EMAIL = "email";
    private static final String EMAIL_VERIFIED = "emailverified";
    private static final String ENABLED = "enabled";
    private static final String FIRST_NAME = "firstname";
    private static final String LAST_NAME = "lastname";
    private static final String UPDATED = "updated";

    private KeycloakSession session;
    private ComponentModel model;

    public DbUserProvider(KeycloakSession session, ComponentModel model) {
        this.session = session;
        this.model = model;
    }

    public void importUser(RealmModel realm, ComponentModel model, Map<String, Object> data) {
        UserModel user = session.userLocalStorage().addUser(realm, (String) data.get(USERNAME));

        LOGGER.debugv("User class {0}", user.getClass());

        user.setFederationLink(model.getId());

        user.setEmail((String) data.get(EMAIL));
        user.setEmailVerified(toBoolean(data, EMAIL_VERIFIED));
        user.setEnabled(toBoolean(data, ENABLED));
        user.setFirstName((String) data.get(FIRST_NAME));
        user.setLastName((String) data.get(LAST_NAME));

        user.setSingleAttribute(UPDATED, ((Timestamp) data.get(UPDATED)).toString());
    }

    @Override
    public void close() {
        // Do nothing
    }

    @Override
    public UserModel validate(RealmModel realm, UserModel user) {
        // TODO: Check if the user exists in the external source
        // Return null to remove localy
        return user;
    }

    private boolean toBoolean(Map<String, Object> data, String key) {
        return data.get(key) != null ? (Boolean) data.get(key) : false;
    }

}
