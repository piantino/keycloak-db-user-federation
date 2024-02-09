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
SELECT username, email, email_verified, first_name, last_name, enabled, updated, custom_attry FROM users WHERE username = ?
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
| <Custom>         |          | Any                | With be a string attribute           |

Attention, no empty or blank string is allowed, use NULL instead.

## Required Actions

* CONFIGURE_RECOVERY_AUTHN_CODES
* CONFIGURE_TOTP
* TERMS_AND_CONDITIONS
* UPDATE_PASSWORD
* UPDATE_PROFILE
* VERIFY_EMAIL
* VERIFY_PROFILE

## Development

### Test CI locally

Install act (https://github.com/nektos/act) and run the command:

`act -P ubuntu-latest=quay.io/jamezp/act-maven`
