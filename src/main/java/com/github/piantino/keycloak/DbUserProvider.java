package com.github.piantino.keycloak;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;
import org.keycloak.component.ComponentModel;
import org.keycloak.credential.PasswordCredentialProviderFactory;
import org.keycloak.models.Constants;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserModel.RequiredAction;
import org.keycloak.models.utils.KeycloakModelUtils;
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

    public DbUserProvider(KeycloakSession session) {
        this.session = session;
    }

    public Importation importUser(RealmModel realm, ComponentModel model, Map<String, Object> data,
            List<String> roles) {
        String username = (String) data.get(Column.username.name());
        String email = (String) data.get(Column.email.name());

        validateDbData(username, email, data);
        Set<RequiredAction> actions = getRequiredActions(data);

        UserModel user = session.users().getUserByUsername(realm, username);

        Importation importation;

        if (user == null) {
            user = session.users().addUser(realm, username);
            user.setFederationLink(model.getId());
            importation = Importation.ADDED;
            LOGGER.debugv("{0} - Created user {1}", realm.getId(), username);

            addRequiredActions(user, actions);
            createCredential(realm, user, data);
        } else if (!model.getId().equals(user.getFederationLink())) {
            throw new DbUserProviderException(
                    realm.getId() + " - Local user not created from importation: " + username);
        } else {
            importation = Importation.UPDATED;
        }

        user.setEmail(email);
        user.setEmailVerified(toBoolean(data, Column.email_verified));
        user.setEnabled(toBoolean(data, Column.enabled));
        user.setFirstName((String) data.get(Column.first_name.name()));
        user.setLastName((String) data.get(Column.last_name.name()));
        user.setSingleAttribute(Column.updated.name(), toAttribute(data.get(Column.updated.name())));

        updateAttributes(user, data);
        importRoles(realm, user, roles);

        user.setSingleAttribute("synched", LocalDateTime.now().toString());

        return importation;
    }

    private void importRoles(RealmModel realm, UserModel user, List<String> roles) {
        RoleModel roleRoot = getRoleRoot(realm);

        List<RoleModel> actualRoles = user.getRealmRoleMappingsStream().collect(Collectors.toList());
        List<RoleModel> newRoles = roles.stream().map(roleName -> getRole(realm, roleRoot, roleName))
                .collect(Collectors.toList());

        actualRoles.stream().filter(r -> !newRoles.contains(r)).forEach(user::deleteRoleMapping);

        newRoles.add(getDefaultRole(realm));
        newRoles.stream().filter(r -> !user.hasRole(r))
                .forEach(user::grantRole);
    }

    private RoleModel getRole(RealmModel realm, RoleModel roleRoot, String roleName) {
        RoleModel role = KeycloakModelUtils.getRoleFromString(realm, roleName);

        if (role == null) {
            role = session.roles().addRealmRole(realm, roleName);
            role.setDescription("db-user-provider");

            roleRoot.addCompositeRole(role);

            LOGGER.debugv("{0} - Created role {1}", realm.getId(), roleName);
        }
        return role;
    }

    private RoleModel getRoleRoot(RealmModel realm) {
        String rootRoleName = "db-user-provider-roles";
        RoleModel roleRoot = KeycloakModelUtils.getRoleFromString(realm, rootRoleName);

        if (roleRoot == null) {
            roleRoot = session.roles().addRealmRole(realm, rootRoleName);
            roleRoot.setDescription("db-user-provider");
        }
        return roleRoot;
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
                .forEach(entry -> user.setSingleAttribute(entry.getKey(), toAttribute(entry.getValue())));
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

    private String toAttribute(Object value) {
        if (value instanceof Timestamp) {
            Timestamp time = (Timestamp) value;
            return time.toLocalDateTime().toString();
        }
        return value.toString();
    }

    private boolean hasColumn(Map<String, Object> data, Column column) {
        return data.get(column.name()) != null;
    }

    private RoleModel getDefaultRole(RealmModel realm) {
        String roleName = Constants.DEFAULT_ROLES_ROLE_PREFIX + "-" + realm.getId();
        return KeycloakModelUtils.getRoleFromString(realm, roleName);
    }

}
