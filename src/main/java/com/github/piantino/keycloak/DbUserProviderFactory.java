package com.github.piantino.keycloak;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.apache.commons.lang3.RandomStringUtils;
import org.jboss.logging.Logger;
import org.keycloak.component.ComponentModel;
import org.keycloak.component.ComponentValidationException;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.KeycloakSessionTask;
import org.keycloak.models.RealmModel;
import org.keycloak.models.StorageProviderRealmModel;
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
import io.agroal.api.AgroalDataSourceMetrics;

public class DbUserProviderFactory implements UserStorageProviderFactory<DbUserProvider>, ImportSynchronization {
    private static final int LOG_PARTIAL_COUNT = 1000;

    protected static final Logger LOGGER = Logger.getLogger(DbUserProviderFactory.class);

    public static final String PROVIDER_ID = "db-user-provider";

    private static final List<ProviderConfigProperty> CONFIG_PROPERTIES;
    static {
        List<ProviderConfigProperty> config = DataSouceConfiguration.create();
        CONFIG_PROPERTIES = Collections.unmodifiableList(config);
    }

    private static final Map<String, AgroalDataSource> DB_BY_MODEL_ID = new ConcurrentHashMap<>();

    @Override
    public String getId() {
        return PROVIDER_ID;
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

        String importId = createImportId();
        LOGGER.infov("[{0}] Sync all {1} started", importId, realmId);

        String sql = model.get(DataSouceConfiguration.SYNC_SQL);
        SynchronizationResult result = importUsers(importId, sessionFactory, realmId, model, sql, (ps) -> {
        });
        LOGGER.infov("[{0}] Sync all {1} finished: {2}", importId, realmId, result);
        return result;
    }

    @Override
    public SynchronizationResult syncSince(Date lastSync, KeycloakSessionFactory sessionFactory, String realmId,
            UserStorageProviderModel model) {

        String importId = createImportId();
        Timestamp timeStamp = new Timestamp(lastSync.getTime());

        LOGGER.infov("[{0}] Sync since {1} started {2}", importId, realmId, timeStamp);

        String sql = model.get(DataSouceConfiguration.SYNC_SINCE_SQL);

        SynchronizationResult result = importUsers(importId, sessionFactory, realmId, model, sql, (ps) -> {
            try {
                ps.setTimestamp(1, timeStamp);
            } catch (SQLException e) {
                throw new DbUserProviderException("Error configure sync since " + timeStamp + " in " + realmId, e);
            }
        });
        LOGGER.infov("[{0}] Sync since {1} finished: {2}", importId, realmId, result);
        return result;
    }

    @Override
    public void close() {
        for (Entry<String, AgroalDataSource> entry : DB_BY_MODEL_ID.entrySet()) {
            LOGGER.debugv("Close DataSource {0}", entry.getKey());
            entry.getValue().close();
        }
    }

    public SynchronizationResult syncUsername(String username, KeycloakSessionFactory sessionFactory, String realmId,
            UserStorageProviderModel model) {

        String importId = createImportId();

        LOGGER.infov("[{0}] Sync user {1} {2}", importId, realmId, username);

        String sql = model.get(DataSouceConfiguration.SYNC_ONE_SQL);
        SynchronizationResult result = importUsers(importId, sessionFactory, realmId, model, sql, (ps) -> {
            try {
                ps.setString(1, username);
            } catch (SQLException e) {
                throw new DbUserProviderException("Error configure sync user " + username + " in " + realmId, e);
            }
        });
        LOGGER.infov("[{0}] Sync user {1} {2}: ({3})", importId, realmId, username, result);

        return result;
    }

    public static AgroalDataSource getDataSource(RealmModel realm) {
        UserStorageProviderModel model = getModel(realm);
        return getDataSource(model);
    }

    public static AgroalDataSourceMetrics getDataSourceMetrics(RealmModel realm) {
        UserStorageProviderModel model = getModel(realm);
        return getDataSource(model).getMetrics();
    }

    public static UserStorageProviderModel getModel(RealmModel realm) {
		return ((StorageProviderRealmModel) realm).getUserStorageProvidersStream()
				.filter(fedProvider -> Objects.equals(fedProvider.getProviderId(), DbUserProviderFactory.PROVIDER_ID))
				.findFirst()
				.orElseThrow(() -> new DbUserProviderException(DbUserProviderFactory.PROVIDER_ID + " not configured"));
	}

    private SynchronizationResult importUsers(String importId, KeycloakSessionFactory sessionFactory, String realmId,
            UserStorageProviderModel model, String sql, Consumer<PreparedStatement> psConsumer) {

        SynchronizationResult result = new SynchronizationResult();

        AgroalDataSource ds = DbUserProviderFactory.getDataSource(model);

        try (Connection con = ds.getConnection();
                PreparedStatement ps = con.prepareStatement(sql, ResultSet.TYPE_SCROLL_INSENSITIVE,
                ResultSet.CONCUR_READ_ONLY);) {

            psConsumer.accept(ps);

            try (ResultSet rs = ps.executeQuery();) {

                ResultSetMetaData md = rs.getMetaData();
                int columns = md.getColumnCount();

                float total = getUserTotal(importId, rs);
                int counter = 0;

                while (rs.next()) {
                    counter++;

                    HashMap<String, Object> data = new HashMap<String, Object>(columns);
                    for (int i = 1; i <= columns; ++i) {
                        data.put(md.getColumnName(i).toLowerCase(), rs.getObject(i));
                    }
                    String username = (String) data.get(Column.username.toString());

                    logDebugData(importId, data);

                    // Process each user in it's own transaction to avoid global fail
                    KeycloakModelUtils.runJobInTransaction(sessionFactory, new KeycloakSessionTask() {

                        @Override
                        public void run(KeycloakSession session) {
                            RealmModel currentRealm = session.realms().getRealm(realmId);
                            session.getContext().setRealm(currentRealm);

                            DbUserProvider provider = create(session, model);

                            try {

                                List<String> roles = getRoles(model, con, username);
                                Importation importation = provider.importUser(importId, currentRealm, model, data,
                                        roles);
                                if (importation == Importation.ADDED) {
                                    result.increaseAdded();
                                } else {
                                    result.increaseUpdated();
                                }
                            } catch (Throwable e) {
                                result.increaseFailed();
                                LOGGER.errorv(e, "[{0}] Sync error {1}", importId, username);
                            }
                        }
                    });

                    logPartial(importId, total, counter);
                }
            }
        } catch (SQLException e) {
            throw new DbUserProviderException("Error on connect to database in " + realmId, e);
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

    private static AgroalDataSource getDataSource(UserStorageProviderModel model) {
        return DB_BY_MODEL_ID.computeIfAbsent(model.getParentId(), key -> {
            LOGGER.debugv("Creating DataSource {0}", model.getParentId());
            return DataSourceProvider.create(model);
        });
    }

    private String createImportId() {
        return RandomStringUtils.secure().nextAlphanumeric(7);
    }

    private int getUserTotal(String importId, ResultSet rs) throws SQLException {
        try {
            int rowCount = rs.last() ? rs.getRow() : 0;
            rs.beforeFirst();
            LOGGER.debugv("[{0}] Sync total {1}", importId, rowCount);
            return rowCount;
        } catch (SQLFeatureNotSupportedException e) {
            LOGGER.warnv("[{0}] Sync total unknow (JDBC driver feature not supported)", importId);
            return -1;
        }
    }

    private void logDebugData(String importId, HashMap<String, Object> data) {
        if (LOGGER.isDebugEnabled()) {
            Map<String, Object> clone = (HashMap<String, Object>) data.clone();
            clone.computeIfPresent(Column.temp_password.name(), (String x, Object y) -> "***");
            LOGGER.debugv("[{0}] Sync data {1}", importId, clone);
        }
    }

    private void logPartial(String importId, float total, int counter) {
        if (counter % LOG_PARTIAL_COUNT != 0) {
            return;
        }
        if (total > -1) {
            LOGGER.infov("[{0}] Sync partial {1}/{2} ({3,number,#}%)", importId, counter, total, counter / total * 100);
            return;
        }
        LOGGER.infov("[{0}] Sync partial {1}", importId, counter);
    }
}
