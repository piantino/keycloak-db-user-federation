package com.github.piantino.keycloak;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.ws.rs.core.Response;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.keycloak.TokenVerifier;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RoleResource;
import org.keycloak.authorization.client.AuthzClient;
import org.keycloak.authorization.client.Configuration;
import org.keycloak.authorization.client.util.HttpResponseException;
import org.keycloak.common.VerificationException;
import org.keycloak.representations.AccessTokenResponse;
import org.keycloak.representations.IDToken;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.SynchronizationResultRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.ext.ScriptUtils;
import org.testcontainers.jdbc.JdbcDatabaseDelegate;
import org.testcontainers.utility.MountableFile;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.github.piantino.keycloak.rest.DbUserResourceApi;

import dasniko.testcontainers.keycloak.KeycloakContainer;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(OrderAnnotation.class)
public class DbUserProviterTest {

        private static final String USER_PROVIDER_ID = "d3766429-5ef2-4e7e-972a-2ee2872e6929";
        private static final String DB_URL = "jdbc:postgresql://postgres:5432/test-db?loggerLevel=OFF&options=-c timezone=America/Sao_Paulo";

        private Network network = Network.newNetwork();

        private KeycloakContainer keycloak = new KeycloakContainer("quay.io/keycloak/keycloak:24.0.1")
                        .withAdminUsername("admin")
                        .withAdminPassword("admin123")
                        .withProviderClassesFrom("target/classes")
                        .withDebug()
                        .withDebugFixedPort(8000, false)
                        .withExposedPorts(8080)
                        .withNetwork(network)
                        .withRealmImportFile("realm-export.json")
                        .withCopyFileToContainer(MountableFile.forClasspathResource("keycloak.conf"),
                                        "/opt/keycloak/conf/keycloak.conf")
                        .withEnv("KC_DB", "postgres")
                        .withEnv("KC_DB_URL", DB_URL)
                        .withEnv("KC_DB_USERNAME", "sa")
                        .withEnv("KC_DB_PASSWORD", "sa")
                        .withEnv("TZ", "America/Sao_Paulo");

        private PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:13.11")
                        .withDatabaseName("test-db")
                        .withUsername("sa")
                        .withPassword("sa")
                        .withNetwork(network)
                        .withNetworkAliases("postgres")
                        .withInitScript("init-script.sql")
                        .withEnv("TZ", "America/Sao_Paulo");

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

                System.out.println("Configuration: " + keycloak.getAuthServerUrl() + "/admin/master/console/#/db-user-realm/user-federation/db-user-provider/" + USER_PROVIDER_ID);
        }

        @Test
        @Order(1)
        public void importAllUsersFromDB() {
                SynchronizationResultRepresentation result = realm.userStorage().syncUsers(USER_PROVIDER_ID,
                                "triggerFullSync");

                assertEquals(8, result.getAdded(), "Added");
                assertEquals(0, result.getUpdated(), "Updated");
                assertEquals(5, result.getFailed(), "Failed");
        }

        @Test
        @Order(2)
        public void validateImportation() {
                int userCount = realm.users().count();
                assertEquals(8, userCount, "Users imported");

                UserRepresentation user = realm.users().search("presto").get(0);

                assertEquals("presto@wizard.com", user.getEmail(), "Email");
                assertEquals(true, user.isEmailVerified(), "Email verified");
                assertEquals("Presto", user.getFirstName(), "First name");
                assertEquals("Wizard", user.getLastName(), "Last name");
                assertEquals(true, user.isEnabled(), "Enabled");
                assertNotNull(user.getAttributes().get("updated"), "Updated");
                assertNull(user.getAttributes().get("ability"), "Custom attribute");
                assertEquals(1, user.getRequiredActions().size(), "Required actions");

                user = realm.users().search("uni").get(0);

                assertEquals("uni@unicorn.com", user.getEmail(), "Email");
                assertEquals(false, user.isEmailVerified(), "Email verified");
                assertEquals("Uni", user.getFirstName(), "First name");
                assertEquals("Unicorn", user.getLastName(), "Last name");
                assertEquals(false, user.isEnabled(), "Enabled");
                assertNotNull(user.getAttributes().get("updated"), "Updated");
                assertArrayEquals(Arrays.asList("teleport").toArray(), user.getAttributes().get("ability").toArray(),
                                "Custom attribute");
                assertEquals(0, user.getRequiredActions().size(), "Required actions");
        }

        @Test
        @Order(3)
        public void valitateRoleImportation() {
                RoleResource rootRole = realm.roles().get("db-user-provider-roles");
                assertEquals(4, rootRole.getRealmRoleComposites().size(), "Root role");

                RoleResource role = realm.roles().get("leader");
                assertNotNull(role, "Leader role");

                List<UserRepresentation> roleUserMembers = role.getUserMembers();
                assertEquals(1, roleUserMembers.size(), "There can be only one");
                assertEquals("hank", roleUserMembers.stream().map(u -> u.getUsername()).findFirst().get(), "Role");

                role = realm.roles().get("default-roles-db-user-realm");
                assertNotNull(role, "Default role");

                roleUserMembers = role.getUserMembers();
                assertEquals(realm.users().count(), roleUserMembers.size(), "All users");

                UserRepresentation user = realm.users().search("master").get(0);
                List<String> roles = realm.users().get(user.getId()).roles().realmLevel().listAll()
                                .stream().map(r -> r.getName()).sorted().collect(Collectors.toList());
                List<String> expected = Arrays.asList(
                                "default-roles-db-user-realm", "role1", "role2", "role3");
                assertIterableEquals(expected, roles, "Master roles");
        }

        @Test
        @Order(4)
        public void importChangedUsersFromDB() {
                JdbcDatabaseDelegate containerDelegate = new JdbcDatabaseDelegate(postgres, "");
                ScriptUtils.runInitScript(containerDelegate, "update-script.sql");

                SynchronizationResultRepresentation result = realm.userStorage().syncUsers(USER_PROVIDER_ID,
                                "triggerChangedUsersSync");

                assertEquals(1, result.getAdded(), "Added");
                assertEquals(3, result.getUpdated(), "Updated");
                assertEquals(0, result.getFailed(), "Failed");
        }

        @Test
        @Order(5)
        public void valitateRoleChanged() {
                RoleResource rootRole = realm.roles().get("db-user-provider-roles");
                assertEquals(8, rootRole.getRealmRoleComposites().size(), "Root role");

                RoleResource role = realm.roles().get("leader");
                assertNotNull(role, "Leader role");

                List<UserRepresentation> roleUserMembers = role.getUserMembers();
                assertEquals(0, roleUserMembers.size(), "Role removed");

                role = realm.roles().get("ex-leader");
                assertNotNull(role, "Ex-leader role");

                roleUserMembers = role.getUserMembers();
                assertEquals(1, roleUserMembers.size(), "Role member");
                assertEquals("hank", roleUserMembers.stream().map(u -> u.getUsername()).findFirst().get(), "Role");

                UserRepresentation user = realm.users().search("master").get(0);
                List<String> roles = realm.users().get(user.getId()).roles().realmLevel().listAll()
                                .stream().map(r -> r.getName()).sorted().collect(Collectors.toList());
                List<String> expected = Arrays.asList(
                                "default-roles-db-user-realm", "role1", "role3", "role4", "role5", "role6");
                assertIterableEquals(expected, roles, "Master roles");
        }

        @Test
        @Order(6)
        public void validateUpdate() {
                UserRepresentation user = realm.users().search("uni").get(0);
                assertEquals("uni@unicorn.com", user.getEmail(), "Email");
                assertEquals(false, user.isEmailVerified(), "Email verified");
                assertEquals("Venger", user.getFirstName(), "First name");
                assertEquals("Wizard", user.getLastName(), "Last name");
                assertEquals(false, user.isEnabled(), "Enabled");
                assertNotNull(user.getAttributes().get("updated"), "Updated");
                assertArrayEquals(Arrays.asList("spells").toArray(), user.getAttributes().get("ability").toArray(),
                                "Custom attribute");
        }

        @Test
        @Order(7)
        public void syncUserWithApi() throws URISyntaxException {
                JdbcDatabaseDelegate containerDelegate = new JdbcDatabaseDelegate(postgres, "");
                ScriptUtils.runInitScript(containerDelegate, "update-script2.sql");

                String apiPath = keycloak.getAuthServerUrl() + "/admin/realms/db-user-realm/db-user/";
                DbUserResourceApi resource = client.proxy(DbUserResourceApi.class, new URI(apiPath));
                resource.sync("master");

                UserRepresentation user = realm.users().search("master").get(0);
                assertEquals("Missing", user.getFirstName(), "First name");
                assertEquals(false, user.isEnabled(), "Disabled");
        }

        @Test
        @Order(8)
        public void createAppClient() {
                ClientRepresentation clientRepresentation = new ClientRepresentation();
                clientRepresentation.setId("my-app");
                clientRepresentation.setName("My App Client");
                clientRepresentation.setSecret("SoBy2IbD8fYPPP2iM6sNNqVcrkl57Qie");
                clientRepresentation.setDirectAccessGrantsEnabled(true);

                Response response = realm.clients().create(clientRepresentation);
                assertEquals(201, response.getStatus(), "Status HTTP created");
        }

        @ParameterizedTest
        @Order(9)
        @CsvSource(value = {
                        "bobby,ThunderClub,INVALID",
                        "eric,GriffonShield,INVALID",
                        "diana,JavelinStaff,INVALID",
                        "hank,EnergyBow,INVALID",
                        "presto,HatofManySpells,INVALID",
                        "sheila,CloakofInvisibility,CORRECT",
                        "uni,NoPassword,INVALID",
                        "invalid_user,invalid,INVALID"
        })
        public void validatePasswordImportation(String username, String password, String expected)
                        throws VerificationException {
                Map<String, Object> credentials = new HashMap<String, Object>();
                credentials.put("secret", "SoBy2IbD8fYPPP2iM6sNNqVcrkl57Qie");
                Configuration configuration = new Configuration(keycloak.getAuthServerUrl(), "db-user-realm", "my-app",
                                credentials, null);

                AuthzClient authzClient = AuthzClient.create(configuration);

                try {
                        AccessTokenResponse response = authzClient.obtainAccessToken(username, password);

                        if (expected.equals("INVALID")) {
                                fail("Expected invalid credential");
                        }
                        assertNotNull(response.getToken(), "ID Token");

                        IDToken token = TokenVerifier.create(response.getToken(), IDToken.class).getToken();
                        assertEquals(username, token.getPreferredUsername(), "Username");

                } catch (HttpResponseException e) {
                        if (expected.equals("CORRECT")) {
                                fail("Invalid credential", e);
                        }
                        int expectedStatus = username.equals("invalid_user") ? 401 : 400;
                        assertEquals(expectedStatus, e.getStatusCode(), "Status HTTP");
                }
        }

        @Test
        @Order(10)
        public void databaseMetrics() throws URISyntaxException {
                String apiPath = keycloak.getAuthServerUrl() + "/admin/realms/db-user-realm/db-user/";
                DbUserResourceApi resource = client.proxy(DbUserResourceApi.class, new URI(apiPath));
                String metrics = resource.metrics();

                assertNotEquals("Metrics Disabled", metrics);
        }
}

