package com.github.piantino.keycloak;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.logging.Logger;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.KeycloakSessionTask;
import org.keycloak.models.RealmModel;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.storage.UserStorageProvider;
import org.keycloak.storage.UserStorageProviderFactory;
import org.keycloak.storage.UserStorageProviderModel;
import org.keycloak.storage.user.ImportSynchronization;
import org.keycloak.storage.user.SynchronizationResult;

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

        SynchronizationResult result = new SynchronizationResult();

        String sql = model.get(DataSouceConfiguration.SQL);

        try (AgroalDataSource ds = DataSourceProvider.create(model);
                Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(sql);) {
            try (ResultSet rs = ps.executeQuery();) {

                ResultSetMetaData md = rs.getMetaData();
                int columns = md.getColumnCount();

                while (rs.next()) {
                    Map<String, Object> row = new HashMap<String, Object>(columns);
                    for (int i = 1; i <= columns; ++i) {
                        row.put(md.getColumnName(i), rs.getObject(i));
                    }
                    LOGGER.debugv("User {0}", row);

                    // Process each user in it's own transaction to avoid global fail
                    KeycloakModelUtils.runJobInTransaction(sessionFactory, new KeycloakSessionTask() {

                        @Override
                        public void run(KeycloakSession session) {
                            RealmModel currentRealm = session.realms().getRealm(realmId);
                            session.getContext().setRealm(currentRealm);

                            DbUserProvider provider = create(session, model);
                            provider.importUser(currentRealm, model, row);

                            result.increaseAdded();
                        }
                    });
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return result;
    }

    @Override
    public SynchronizationResult syncSince(Date lastSync, KeycloakSessionFactory sessionFactory, String realmId,
            UserStorageProviderModel model) {
        SynchronizationResult result = new SynchronizationResult();

        return result;
    }

}
