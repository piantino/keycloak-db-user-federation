package com.github.piantino.keycloak;

import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.SynchronizationResultRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

import dasniko.testcontainers.keycloak.KeycloakContainer;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class DbUserProviterTest {

    private static final String USER_PROVIDER_ID = "d3766429-5ef2-4e7e-972a-2ee2872e6929";

    private Network network = Network.newNetwork();

    private KeycloakContainer keycloak = new KeycloakContainer("quay.io/keycloak/keycloak:18.0.2")
            .withAdminUsername("admin")
            .withAdminPassword("tops3cr3t")
            .withProviderClassesFrom("target/classes")
            // .withEnv("DEBUG_MODE", "true")
            // .withEnv("DEBUG_PORT", "8000")
            // .withEnv("JAVA_OPTS",
            // "-agentlib:jdwp=transport=dt_socket,server=y,address=*:8000,suspend=n")
            // .withExposedPorts(8000)
            .withNetwork(network)
            .withRealmImportFile("realm-export.json")
            .withCopyFileToContainer(MountableFile.forClasspathResource("keycloak.conf"),
                    "/opt/keycloak/conf/keycloak.conf");

    private PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:13.11")
            .withDatabaseName("test-db")
            .withUsername("sa")
            .withPassword("sa")
            .withNetwork(network)
            .withNetworkAliases("postgres")
            .withInitScript("init-script.sql");

    private Keycloak client;
    private RealmResource realm;

    @BeforeAll
    public void init() throws JsonParseException, JsonMappingException, IOException {

        Stream.of(keycloak, postgres).parallel().forEach(GenericContainer::start);

        client = KeycloakBuilder.builder()
                .serverUrl(keycloak.getAuthServerUrl())
                .realm("master")
                .clientId("admin-cli")
                .username(keycloak.getAdminUsername())
                .password(keycloak.getAdminPassword())
                .build();

        realm = client.realm("db-user-realm");
    }

    @Test
    public void importUsersFromDB() {
        SynchronizationResultRepresentation result = realm.userStorage().syncUsers(USER_PROVIDER_ID, "triggerFullSync");

        Assertions.assertEquals(7, result.getAdded(), "Added");
        Assertions.assertEquals(0, result.getUpdated(), "Updated");
    }

    @Test
    public void validateImportation() {
        UsersResource resource = realm.users();
        
        List<UserRepresentation> users = resource.list();
        Assertions.assertEquals(7, users.size(), "Users imported");

        UserRepresentation user = users.get(4);

        Assertions.assertEquals("presto@wizard.com", user.getEmail(), "Email");
        Assertions.assertEquals(true, user.isEmailVerified(), "Email verified");
        Assertions.assertEquals("Presto", user.getFirstName(), "First name");
        Assertions.assertEquals("Wizard", user.getLastName(), "Last name");
        Assertions.assertEquals(true, user.isEnabled(), "Enabled");

        user = users.get(6);

        Assertions.assertEquals("uni@unicorn.com", user.getEmail(), "Email");
        Assertions.assertEquals(false, user.isEmailVerified(), "Email verified");
        Assertions.assertEquals("Uni", user.getFirstName(), "First name");
        Assertions.assertEquals("Unicorn", user.getLastName(), "Last name");
        Assertions.assertEquals(false, user.isEnabled(), "Enabled");
    }

}
