package com.github.piantino.keycloak.datasource;

import java.util.List;

import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;

import io.agroal.api.configuration.supplier.AgroalPropertiesReader;

public class DataSouceConfiguration {

    public static final String SQL = "sql";

    public static List<ProviderConfigProperty> create() {
        return ProviderConfigurationBuilder.create()
                .property()
                .name(AgroalPropertiesReader.PROVIDER_CLASS_NAME).type(ProviderConfigProperty.STRING_TYPE)
                .helpText("Database provider classe name").add()
                .property()
                .name(AgroalPropertiesReader.JDBC_URL).type(ProviderConfigProperty.STRING_TYPE).add()
                .property()
                .name(AgroalPropertiesReader.PRINCIPAL).type(ProviderConfigProperty.STRING_TYPE).add()
                .property()
                .name(AgroalPropertiesReader.CREDENTIAL).type(ProviderConfigProperty.STRING_TYPE).add()
                .property()
                .name(AgroalPropertiesReader.MAX_SIZE).type(ProviderConfigProperty.STRING_TYPE).add()
                .property()
                .name(AgroalPropertiesReader.MIN_SIZE).type(ProviderConfigProperty.STRING_TYPE).add()
                .property()
                .name(AgroalPropertiesReader.INITIAL_SIZE).type(ProviderConfigProperty.STRING_TYPE).add()
                .property()
                .name(AgroalPropertiesReader.MAX_LIFETIME_S).type(ProviderConfigProperty.STRING_TYPE).add()
                .property()
                .name(AgroalPropertiesReader.ACQUISITION_TIMEOUT_S).type(ProviderConfigProperty.STRING_TYPE).add()
                .property()
                .name(SQL).type(ProviderConfigProperty.TEXT_TYPE).add()
                .build();
    }

}
