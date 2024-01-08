package com.github.piantino.keycloak;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.jboss.logging.Logger;
import org.keycloak.component.ComponentModel;
import org.keycloak.component.ComponentValidationException;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.KeycloakSessionTask;
import org.keycloak.models.RealmModel;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.storage.UserStorageProviderFactory;
import org.keycloak.storage.UserStorageProviderModel;
import org.keycloak.storage.user.ImportSynchronization;
import org.keycloak.storage.user.SynchronizationResult;
import org.keycloak.utils.StringUtil;

import com.github.piantino.keycloak.DbUserProvider.Column;
import com.github.piantino.keycloak.DbUserProvider.Importation;
import com.github.piantino.keycloak.datasource.DataSouceConfiguration;
import com.github.piantino.keycloak.datasource.DataSourceProvider;
import com.github.piantino.keycloak.exception.DbUserProviderException;

import io.agroal.api.AgroalDataSource;

public class DbUserProviderFactory implements UserStorageProviderFactory<DbUserProvider>, ImportSynchronization {

    protected static final Logger LOGGER = Logger.getLogger(DbUserProviderFactory.class);

    private static final List<ProviderConfigProperty> CONFIG_PROPERTIES;
    static {
        List<ProviderConfigProperty> config = DataSouceConfiguration.create();
        CONFIG_PROPERTIES = Collections.unmodifiableList(config);
    }

    private final Map<String, AgroalDataSource> dataSourceByModelId = new ConcurrentHashMap<>();

    @Override
    public String getId() {
        return "db-user-provider";
    }

    @Override
    public DbUserProvider create(KeycloakSession session, ComponentModel model) {
        return new DbUserProvider(session);
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return CONFIG_PROPERTIES;
    }

    @Override
    public void validateConfiguration(KeycloakSession session, RealmModel realm, ComponentModel config)
            throws ComponentValidationException {

        // Add validations
    }

    @Override
    public SynchronizationResult sync(KeycloakSessionFactory sessionFactory, String realmId,
            UserStorageProviderModel model) {

        String sql = model.get(DataSouceConfiguration.SYNC_SQL);
        SynchronizationResult result = importUsers(sessionFactory, realmId, model, sql, (ps) -> {
        });
        LOGGER.infov("Sync all: {0}", result);
        return result;
    }

    @Override
    public SynchronizationResult syncSince(Date lastSync, KeycloakSessionFactory sessionFactory, String realmId,
            UserStorageProviderModel model) {

        Timestamp timeStamp = new Timestamp(lastSync.getTime());
        LOGGER.debugv("Search users updated since {0}", timeStamp);

        String sql = model.get(DataSouceConfiguration.SYNC_SINCE_SQL);

        SynchronizationResult result = importUsers(sessionFactory, realmId, model, sql, (ps) -> {
            try {
                ps.setTimestamp(1, timeStamp);
            } catch (SQLException e) {
                throw new DbUserProviderException("Error configure sync since " + timeStamp, e);
            }
        });
        LOGGER.infov("Sync since {0}: {1}", timeStamp, result);
        return result;
    }

    @Override
    public void close() {
        for (Entry<String, AgroalDataSource> entry : dataSourceByModelId.entrySet()) {
            LOGGER.debugv("Close DataSource {0}", entry.getKey());
            entry.getValue().close();
        }
    }

    public SynchronizationResult syncUsername(String username, KeycloakSessionFactory sessionFactory, String realmId,
            UserStorageProviderModel model) {

        String sql = model.get(DataSouceConfiguration.SYNC_ONE_SQL);
        SynchronizationResult result = importUsers(sessionFactory, realmId, model, sql, (ps) -> {
            try {
                ps.setString(1, username);
            } catch (SQLException e) {
                throw new DbUserProviderException("Error configure sync user " + username, e);
            }
        });
        LOGGER.infov("Sync username {0}: {1}", username, result);
        return result;
    }

    private SynchronizationResult importUsers(KeycloakSessionFactory sessionFactory, String realmId,
            UserStorageProviderModel model, String sql, Consumer<PreparedStatement> psConsumer) {

        SynchronizationResult result = new SynchronizationResult();

        AgroalDataSource ds = getDataSource(model);

        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(sql);) {

            psConsumer.accept(ps);

            try (ResultSet rs = ps.executeQuery();) {

                ResultSetMetaData md = rs.getMetaData();
                int columns = md.getColumnCount();

                while (rs.next()) {
                    Map<String, Object> data = new HashMap<String, Object>(columns);
                    for (int i = 1; i <= columns; ++i) {
                        data.put(md.getColumnName(i).toLowerCase(), rs.getObject(i));
                    }
                    String username = (String) data.get(Column.username.toString());

                    LOGGER.debugv("Syncing user {0}", username);

                    // Process each user in it's own transaction to avoid global fail
                    KeycloakModelUtils.runJobInTransaction(sessionFactory, new KeycloakSessionTask() {

                        @Override
                        public void run(KeycloakSession session) {
                            RealmModel currentRealm = session.realms().getRealm(realmId);
                            session.getContext().setRealm(currentRealm);

                            // TODO: Not work session.getProvider(DbUserProvider.class)
                            DbUserProvider provider = create(session, model);

                            try {

                                List<String> roles = getRoles(model, con, username);
                                Importation importation = provider.importUser(currentRealm, model, data, roles);

                                if (importation == Importation.ADDED) {
                                    result.increaseAdded();
                                } else {
                                    result.increaseUpdated();
                                }
                            } catch (Throwable e) {
                                result.increaseFailed();
                                LOGGER.errorv(e, "Error on import {0}",
                                        data.get(DbUserProvider.Column.username.name()));
                            }
                        }
                    });
                }
            }
        } catch (SQLException e) {
            throw new DbUserProviderException("Error on connect to database", e);
        }
        return result;
    }

    private List<String> getRoles(UserStorageProviderModel model, Connection con, String username) throws SQLException {
        String roleSql = model.get(DataSouceConfiguration.SYNC_ROLE_SQL);

        if (StringUtil.isBlank(roleSql)) {
            return Collections.emptyList();
        }

        List<String> roles = new ArrayList<>();

        try (PreparedStatement ps = con.prepareStatement(roleSql);) {

            ps.setString(1, username);

            try (ResultSet rs = ps.executeQuery();) {
                while (rs.next()) {
                    roles.add(rs.getString("name"));
                }
            }
        }
        return roles;
    }

    private AgroalDataSource getDataSource(UserStorageProviderModel model) {
        return dataSourceByModelId.computeIfAbsent(model.getId(), key -> {
            LOGGER.debugv("Creating DataSource {0} ({1})", model.getId(), model.getParentId());
            return DataSourceProvider.create(model);
        });
    }

}
