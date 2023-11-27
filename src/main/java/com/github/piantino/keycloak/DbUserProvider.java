package com.github.piantino.keycloak;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;
import org.keycloak.component.ComponentModel;
import org.keycloak.credential.PasswordCredentialProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserModel.RequiredAction;
import org.keycloak.services.validation.Validation;
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

    // For DB without support to boolean data type
    private static final String TRUE_VALUE = "y";

    private KeycloakSession session;
    private ComponentModel model;

    public DbUserProvider(KeycloakSession session, ComponentModel model) {
        this.session = session;
        this.model = model;
    }

    public Importation importUser(RealmModel realm, ComponentModel model, Map<String, Object> data) {
        String username = (String) data.get(Column.username.name());
        String email = (String) data.get(Column.email.name());

        validateDbData(username, email, data);
        Set<RequiredAction> actions = getRequiredActions(data);

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

        user.setEmail(email);
        user.setEmailVerified(toBoolean(data, Column.email_verified));
        user.setEnabled(toBoolean(data, Column.enabled));
        user.setFirstName((String) data.get(Column.first_name.name()));
        user.setLastName((String) data.get(Column.last_name.name()));
        user.setSingleAttribute(Column.updated.name(), ((Timestamp) data.get(Column.updated.name())).toString());

        updateAttributes(user, data);
        addRequiredActions(user, actions);
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

    private void validateDbData(String username, String email, Map<String, Object> data) {
        if (!Validation.isUsernameValid(username)) {
            throw new DbUserProviderException("User with invalid user name: " + username);
        }

        if (email != null && !Validation.isEmailValid(email)) {
            throw new DbUserProviderException("User with invalid email: " + username);
        }

        for (Map.Entry<String, Object> entry : data.entrySet()) {
            Object value = entry.getValue();

            if (value != null && value.getClass().equals(String.class)
                    && Validation.isBlank((String) value)) {
                throw new DbUserProviderException("User with blank field " + entry.getKey() + ": " + username);
            }
        }
    }

    private void updateAttributes(UserModel user, Map<String, Object> data) {
        data.entrySet().stream()
                .filter(entry -> !Column_KEYS.contains(entry.getKey()))
                .filter(entry -> entry.getValue() != null)
                .forEach(entry -> user.setSingleAttribute(entry.getKey(), entry.getValue().toString()));
    }

    private void createCredential(RealmModel realm, UserModel user, Map<String, Object> data) {
        if (hasColumn(data, Column.temp_password)) {
            // PasswordCredentialProvider passwordProvider =
            // session.getProvider(PasswordCredentialProvider.class);
            PasswordCredentialProviderFactory f = new PasswordCredentialProviderFactory();
            f.create(session).createCredential(realm, user, (String) data.get(Column.temp_password.name()));
        }
    }

    private void addRequiredActions(UserModel user, Set<UserModel.RequiredAction> actions) {
        for (UserModel.RequiredAction action : actions) {
            user.addRequiredAction(action);
        }
    }

    private Set<UserModel.RequiredAction> getRequiredActions(Map<String, Object> data) {
        Set<UserModel.RequiredAction> actions = new HashSet<>();

        if (hasColumn(data, Column.required_actions)) {
            String value = (String) data.get(Column.required_actions.name());

            for (String action : value.split(",")) {
                try {
                    UserModel.RequiredAction requiredAction = UserModel.RequiredAction.valueOf(action.trim());
                    actions.add(requiredAction);
                } catch (IllegalArgumentException e) {
                    throw new DbUserProviderException("Invalid required action: " + action);
                }
            }
        }
        return actions;
    }

    private boolean toBoolean(Map<String, Object> data, Column column) {
        if (!hasColumn(data, column)) {
            return false;
        }
        Object value = data.get(column.name());
        if (value instanceof String) {
            return TRUE_VALUE.equals(((String) value));
        }
        return (Boolean) value;
    }

    private boolean hasColumn(Map<String, Object> data, Column column) {
        return data.get(column.name()) != null;
    }

}
