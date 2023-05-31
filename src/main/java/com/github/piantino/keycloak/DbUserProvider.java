package com.github.piantino.keycloak;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.storage.UserStorageProvider;
import org.keycloak.storage.user.ImportedUserValidation;

import com.github.piantino.keycloak.exception.DbUserProviderException;

public class DbUserProvider implements UserStorageProvider, ImportedUserValidation {

    public enum USER_ATTR {
        username, email, email_verified, enabled, first_name, last_name, updated
    }

    public enum Importation {
        ADDED, UPDATED
    }

    private static final Logger LOGGER = Logger.getLogger(DbUserProvider.class);
    
    private static final List<String> USER_ATTR_KEYS = Arrays.asList(USER_ATTR.values()).stream().map(a -> a.name()).collect(Collectors.toList());
    
    private KeycloakSession session;
    private ComponentModel model;

    public DbUserProvider(KeycloakSession session, ComponentModel model) {
        this.session = session;
        this.model = model;
    }

    public Importation importUser(RealmModel realm, ComponentModel model, Map<String, Object> data) {
        String username = (String) data.get(USER_ATTR.username.name());

        Importation importation = Importation.UPDATED;

        UserModel user = session.userLocalStorage().getUserByUsername(realm, username);
        
        if (user == null) {
            user = session.userLocalStorage().addUser(realm, username);
            user.setFederationLink(model.getId());
            importation = Importation.ADDED;
        } else if (!model.getId().equals(user.getFederationLink())) {
            throw new DbUserProviderException("Local user not created from importation: " + username);
        }

        user.setEmail((String) data.get(USER_ATTR.email.name()));
        user.setEmailVerified(toBoolean(data, USER_ATTR.email_verified.name()));
        user.setEnabled(toBoolean(data, USER_ATTR.enabled.name()));
        user.setFirstName((String) data.get(USER_ATTR.first_name.name()));
        user.setLastName((String) data.get(USER_ATTR.last_name.name()));
        user.setSingleAttribute(USER_ATTR.updated.name(), ((Timestamp) data.get(USER_ATTR.updated.name())).toString());

        for(String key : data.keySet()) {
            if (!USER_ATTR_KEYS.contains(key)) {
                user.setSingleAttribute(key, (String) data.get(key));
            }
        }

        return importation;
    }

    @Override
    public void close() {
        // Do nothing
    }

    @Override
    public UserModel validate(RealmModel realm, UserModel user) {
        // TODO: Check if the user exists in the external source
        // Return null to remove localy UserModel
        return user;
    }

    private boolean toBoolean(Map<String, Object> data, String key) {
        return data.get(key) != null ? (Boolean) data.get(key) : false;
    }

}
