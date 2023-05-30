package com.github.piantino.keycloak.datasource;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import org.keycloak.component.ComponentModel;
import io.agroal.api.AgroalDataSource;
import io.agroal.api.configuration.supplier.AgroalPropertiesReader;

import static io.agroal.api.configuration.supplier.AgroalPropertiesReader.*;

public class DataSourceProvider {

    public static AgroalDataSource create(ComponentModel model) {
        Map<String, String> props = new HashMap<>();

        props.put(PROVIDER_CLASS_NAME, model.get(PROVIDER_CLASS_NAME));
        props.put(JDBC_URL, model.get(JDBC_URL));
        props.put(PRINCIPAL, model.get(PRINCIPAL));
        props.put(CREDENTIAL, model.get(CREDENTIAL));

        props.put(MAX_SIZE, model.get(MAX_SIZE));
        props.put(MIN_SIZE, model.get(MIN_SIZE));
        props.put(INITIAL_SIZE, model.get(INITIAL_SIZE));
        props.put(MAX_LIFETIME_S, model.get(MAX_LIFETIME_S));
        props.put(ACQUISITION_TIMEOUT_S, model.get(ACQUISITION_TIMEOUT_S));
        try {
            return AgroalDataSource.from(new AgroalPropertiesReader().readProperties(props));
        } catch (SQLException e) {
            throw new RuntimeException("Fail to create Datasource in " + model.getParentId(), e);
        }
    }

}
