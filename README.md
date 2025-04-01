# keycloak-db-user-federation

A Keycloak extension for user providers from a database with synchronization in user local storage (Keycloak database).

## Version compatibility with keycloak

| keycloak-db-user-federation | keycloak |
|-----------------------------|----------|
| 1.0.x                       | 18.0.2   |
| 1.1.x                       | 24.0.x   |
| 1.2.x                       | 25.0.x   |

Attention, jump to 24.0 because this security problem:
https://github.com/keycloak/keycloak/security/advisories/GHSA-mpwq-j3xf-7m5w

## Tutorial

### SQL samples

Synchronize all users
```
SELECT username, email, email_verified, first_name, last_name, enabled, temp_password, required_actions, updated, custom_attr FROM users
```

Synchronize changed users
```
SELECT username, email, email_verified, first_name, last_name, enabled, updated, custom_attr FROM users WHERE updated > ?
```

Synchronize a user by username
```
SELECT username, email, email_verified, first_name, last_name, enabled, updated, custom_attr FROM users WHERE username = ?
```

Synchronize Realm Roles
```
SELECT name FROM roles WHERE username = ?
```

### SQL columns

| Colunm name        | Required | Type               | Notes                                |
|--------------------|----------|--------------------|--------------------------------------|
| username           | Yes      | String             |                                      |
| email              |          | String             | A valid e-mail                       |
| email_verified     |          | Boolean or "y"/"n" |                                      |
| enabled            |          | Boolean or "y"/"n" |                                      |
| first_name         |          | String             |                                      |
| last_name          |          | String             |                                      |
| temp_password      |          | String             | Only on creation. Recommended use with UPDATE_PASSWORD |
| required_actions   |          | String             | Only on creation. Separated by comma |
| updated            | Yes      | Timestamp          | When this user was update in DB      |
| marked_for_removal |          | Any                | If exists the user will be removed   |
| <custom_attr>      |          | Any                | With be a string attribute           |

Attention, no empty or blank string is allowed, use NULL instead.

### Required Actions

* CONFIGURE_RECOVERY_AUTHN_CODES
* CONFIGURE_TOTP
* TERMS_AND_CONDITIONS
* UPDATE_PASSWORD
* UPDATE_PROFILE
* VERIFY_EMAIL
* VERIFY_PROFILE

### REST API

#### Synchronize a user by username:

http://localhost:8080/realms/(realm)/db-user/(username)/sync

Return empty.

#### Database metrics:

http://localhost:8080/realms/(realm)/db-user/metrics

Return a text like:
```
===
Connections: 1 created | 0 invalid | 0 reap | 0 flush | 0 destroyed
Pool: 1 available | 0 active | 1 max | 3 acquired | 3 returned
Created duration: 069.830ms average | 69ms max | 69ms total
Acquire duration: 023.486ms average | 70ms max | 70ms total
Threads awaiting: 0
===
```

## Development

### Automated test

Use the `com.github.piantino.keycloak.DbUserProviterTest` to fast development.

Requirements:

* Java 17
* Docker

### Run project for manual test

Build the project:

`mvn clean package`

Start the container:

`VERSION=<project version> docker compose up`

Access the imported realm

http://localhost:8080/admin/master/console/#/db-user-realm/user-federation/db-user-provider/d3766429-5ef2-4e7e-972a-2ee2872e6929

### Test CI locally

Install act (https://github.com/nektos/act) and run the command:

`act -P ubuntu-latest=quay.io/jamezp/act-maven`


## Deploy

`mvn deploy`

> Upon release, your component will be published to Central: this typically occurs within 30 minutes, though updates to search can take up to four hours.

https://central.sonatype.com/

### Deployment history

https://central.sonatype.com/publishing/deployments

### Configuration

https://central.sonatype.org/publish/publish-portal-maven/


The gpg keys can be send manually:

`gpg --output piantino@gmail.com.gpg --export piantino@gmail.com`

https://keys.openpgp.org/upload

