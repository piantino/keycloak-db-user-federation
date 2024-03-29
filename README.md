# keycloak-db-user-federation

A Keycloak extension for user providers from a database with synchronization in user local storage (Keycloak database).

## Version compatibility with keycloak

| keycloak-db-user-federation | keycloak |
|-----------------------------|----------|
| 1.x.x                       | 18.0.2   |

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

| Colunm name      | Required | Type               | Notes          |
|------------------|----------|--------------------|----------------|
| username         | Yes      | String             |                |
| email            |          | String             | A valid e-mail |
| email_verified   |          | Boolean or "y"/"n" |                |
| enabled          |          | Boolean or "y"/"n" |                |
| first_name       |          | String             |                |
| last_name        |          | String             |                |
| temp_password    |          | String             | Only on creation. Recommended use with UPDATE_PASSWORD |
| required_actions |          | String             | Only on creation. Separated by comma |
| updated          | Yes      | Timestamp          | When this user was update in DB      |
| <custom_attr>    |          | Any                | With be a string attribute           |

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

### Test CI locally

Install act (https://github.com/nektos/act) and run the command:

`act -P ubuntu-latest=quay.io/jamezp/act-maven`
