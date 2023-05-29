package com.github.piantino.keycloak;

import java.io.File;
import java.io.IOException;

import org.keycloak.representations.idm.RealmRepresentation;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

// TODO: Remove
public class RealmRepresentationLoader {

    public RealmRepresentation load() throws JsonParseException, JsonMappingException, IOException {
        String resourceName = "realm-export.json";

        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource(resourceName).getFile());

        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(file, RealmRepresentation.class);    
    }

            // RealmRepresentationLoader loader = new RealmRepresentationLoader();
        // RealmRepresentation realm = loader.load();

        // ComponentExportRepresentation component = realm.getComponents().get("org.keycloak.storage.UserStorageProvider")
        //         .get(0);

        // MultivaluedHashMap<String, String> config = component.getConfig();
        // config.putSingle(AgroalPropertiesReader.PROVIDER_CLASS_NAME, postgres.getDriverClassName());
        // config.putSingle(AgroalPropertiesReader.JDBC_URL, "jdbc:postgresql://postgres:5432/integration-tests-db?loggerLevel=OFF");
        // config.putSingle(AgroalPropertiesReader.PRINCIPAL, postgres.getUsername());
        // config.putSingle(AgroalPropertiesReader.CREDENTIAL, postgres.getPassword());

        // client.realms().create(realm);

}
