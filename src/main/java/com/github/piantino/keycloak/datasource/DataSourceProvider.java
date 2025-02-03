package com.github.piantino.keycloak.datasource;


import static io.agroal.api.configuration.supplier.AgroalPropertiesReader.ACQUISITION_TIMEOUT_S;
import static io.agroal.api.configuration.supplier.AgroalPropertiesReader.CREDENTIAL;
import static io.agroal.api.configuration.supplier.AgroalPropertiesReader.INITIAL_SIZE;
import static io.agroal.api.configuration.supplier.AgroalPropertiesReader.JDBC_URL;
import static io.agroal.api.configuration.supplier.AgroalPropertiesReader.MAX_LIFETIME_S;
import static io.agroal.api.configuration.supplier.AgroalPropertiesReader.MAX_SIZE;
import static io.agroal.api.configuration.supplier.AgroalPropertiesReader.METRICS_ENABLED;
import static io.agroal.api.configuration.supplier.AgroalPropertiesReader.MIN_SIZE;
import static io.agroal.api.configuration.supplier.AgroalPropertiesReader.PRINCIPAL;
import static io.agroal.api.configuration.supplier.AgroalPropertiesReader.PROVIDER_CLASS_NAME;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import org.keycloak.component.ComponentModel;

import io.agroal.api.AgroalDataSource;
import io.agroal.api.configuration.supplier.AgroalPropertiesReader;

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
        props.put(METRICS_ENABLED, model.get(METRICS_ENABLED));

        try {
            return AgroalDataSource.from(new AgroalPropertiesReader().readProperties(props));       
        } catch (SQLException e) {
            throw new RuntimeException("Fail to create Datasource in " + model.getParentId(), e);
        }
    }

}
