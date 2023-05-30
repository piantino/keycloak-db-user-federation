package com.github.piantino.keycloak;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.logging.Logger;
import org.keycloak.component.ComponentModel;
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

import com.github.piantino.keycloak.DbUserProvider.Importation;
import com.github.piantino.keycloak.datasource.DataSouceConfiguration;
import com.github.piantino.keycloak.datasource.DataSourceProvider;

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
        return new DbUserProvider(session, model);
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return CONFIG_PROPERTIES;
    }

    @Override
    public SynchronizationResult sync(KeycloakSessionFactory sessionFactory, String realmId,
            UserStorageProviderModel model) {

        String sql = model.get(DataSouceConfiguration.SYNC_SQL);
        return importUsers(sessionFactory, realmId, model, sql, null);
    }

    @Override
    public SynchronizationResult syncSince(Date lastSync, KeycloakSessionFactory sessionFactory, String realmId,
            UserStorageProviderModel model) {

        LOGGER.debugv("Search users updated since {0}", lastSync);

        String sql = model.get(DataSouceConfiguration.SYNC_SINCE_SQL);
        return importUsers(sessionFactory, realmId, model, sql, lastSync);
    }

    @Override
    public void close() {
        for (Entry<String, AgroalDataSource> entry : dataSourceByModelId.entrySet()) {
            entry.getValue().close();
        }
    }

    private SynchronizationResult importUsers(KeycloakSessionFactory sessionFactory, String realmId,
            UserStorageProviderModel model, String sql, Date lastSync) {

        SynchronizationResult result = new SynchronizationResult();

        AgroalDataSource ds = getDataSource(model);

        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(sql);) {

            if (lastSync != null) {
                ps.setTimestamp(1, new Timestamp(lastSync.getTime()));
            }
            try (ResultSet rs = ps.executeQuery();) {

                ResultSetMetaData md = rs.getMetaData();
                int columns = md.getColumnCount();

                while (rs.next()) {
                    Map<String, Object> data = new HashMap<String, Object>(columns);
                    for (int i = 1; i <= columns; ++i) {
                        data.put(md.getColumnName(i).toLowerCase(), rs.getObject(i));
                    }
                    LOGGER.debugv("User {0}", data);

                    // Process each user in it's own transaction to avoid global fail
                    KeycloakModelUtils.runJobInTransaction(sessionFactory, new KeycloakSessionTask() {

                        @Override
                        public void run(KeycloakSession session) {
                            RealmModel currentRealm = session.realms().getRealm(realmId);
                            session.getContext().setRealm(currentRealm);

                            DbUserProvider provider = create(session, model);

                            try {
                                Importation importation = provider.importUser(currentRealm, model, data);
                                if (importation == Importation.ADDED) {
                                    result.increaseAdded();
                                } else {
                                    result.increaseUpdated();
                                }
                            } catch (Throwable e) {
                                result.increaseRemoved();
                                LOGGER.errorv(e, "Error on import {0}", data.get(DbUserProvider.USER_ATTR.username.name()));
                            }
                        }
                    });
                }
            }
        } catch (SQLException e) {
            LOGGER.errorv(e, "Error on connect to database");
        }
        return result;
    }

    private AgroalDataSource getDataSource(UserStorageProviderModel model) {
        return dataSourceByModelId.computeIfAbsent(model.getId(),
                key -> DataSourceProvider.create(model));
    }

}
