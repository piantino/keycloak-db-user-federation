package com.github.piantino.keycloak;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;
import org.keycloak.component.ComponentModel;
import org.keycloak.credential.PasswordCredentialProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.storage.UserStorageProvider;
import org.keycloak.storage.user.ImportedUserValidation;

import com.github.piantino.keycloak.exception.DbUserProviderException;

public class DbUserProvider implements UserStorageProvider, ImportedUserValidation {

    public enum Column {
        username, email, email_verified, enabled, first_name, last_name, temp_password, required_actions, updated
    }

    public enum Importation {
        ADDED, UPDATED
    }

    private static final Logger LOGGER = Logger.getLogger(DbUserProvider.class);

    private static final List<String> Column_KEYS = Arrays.asList(Column.values()).stream().map(a -> a.name())
            .collect(Collectors.toList());

    private KeycloakSession session;
    private ComponentModel model;

    public DbUserProvider(KeycloakSession session, ComponentModel model) {
        this.session = session;
        this.model = model;
    }

    public Importation importUser(RealmModel realm, ComponentModel model, Map<String, Object> data) {
        String username = (String) data.get(Column.username.name());
        UserModel user = session.userLocalStorage().getUserByUsername(realm, username);

        Importation importation;

        if (user == null) {
            user = session.userLocalStorage().addUser(realm, username);
            user.setFederationLink(model.getId());
            importation = Importation.ADDED;
        } else if (!model.getId().equals(user.getFederationLink())) {
            throw new DbUserProviderException("Local user not created from importation: " + username);
        } else {
            importation = Importation.UPDATED;
        }

        user.setEmail((String) data.get(Column.email.name()));
        user.setEmailVerified(toBoolean(data, Column.email_verified));
        user.setEnabled(toBoolean(data, Column.enabled));
        user.setFirstName((String) data.get(Column.first_name.name()));
        user.setLastName((String) data.get(Column.last_name.name()));
        user.setSingleAttribute(Column.updated.name(), ((Timestamp) data.get(Column.updated.name())).toString());

        updateAttributes(user, data);
        addRequiredActions(user, data);
        createCredential(realm, user, data);

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

    private void updateAttributes(UserModel user, Map<String, Object> data) {
        for (String key : data.keySet()) {
            if (!Column_KEYS.contains(key)) {
                user.setSingleAttribute(key, (String) data.get(key));
            }
        }
    }

    private void createCredential(RealmModel realm, UserModel user, Map<String, Object> data) {
        if (hasColumn(data, Column.temp_password)) {
            // PasswordCredentialProvider passwordProvider =
            // session.getProvider(PasswordCredentialProvider.class);
            PasswordCredentialProviderFactory f = new PasswordCredentialProviderFactory();
            f.create(session).createCredential(realm, user, (String) data.get(Column.temp_password.name()));
        }
    }

    private void addRequiredActions(UserModel user, Map<String, Object> data) {
        if (hasColumn(data, Column.required_actions)) {
            String value = (String) data.get(Column.required_actions.name());

            if (value.isBlank()) {
                return;
            }

            for (String action : value.split(",")) {
                try {
                    UserModel.RequiredAction requiredAction = UserModel.RequiredAction.valueOf(action.trim());
                    user.addRequiredAction(requiredAction);
                } catch(IllegalArgumentException e ) {
                    throw new DbUserProviderException("Invalid required action: " + action, e);
                }
            }
        }
    }

    private boolean toBoolean(Map<String, Object> data, Column column) {
        return hasColumn(data, column) ? (Boolean) data.get(column.name()) : false;
    }

    private boolean hasColumn(Map<String, Object> data, Column column) {
        return data.get(column.name()) != null;
    }

}
