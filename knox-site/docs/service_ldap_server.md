# Knox LDAP Service

The Knox LDAP Service provides an embedded LDAP server within the Knox Gateway. It acts as a pluggable LDAP proxy or facade, allowing Knox to expose a standard LDAP interface to clients while fetching user and group information from various backends.

## Overview

Introduced in [KNOX-3247](https://issues.apache.org/jira/browse/KNOX-3247), the Knox LDAP Service leverages Apache Directory Server (ApacheDS) to provide a lightweight, embedded LDAP server. Its primary goal is to provide a consistent LDAP interface for authentication and group lookups, even when the underlying identity store is not a traditional LDAP server or is a remote server that requires proxying.

Key features include:
- **Pluggable LDAP Interceptors**: Support for customization of LDAP search behavior including combining results from multiple LDAP backends.
- **Pluggable Backends**: Support for multiple, different data sources (JSON files, remote LDAP/AD).
- **Embedded Server**: No need for an external LDAP server for simple use cases or testing.
- **Active Directory Integration**: Optimized for proxying to AD with support for `sAMAccountName` and `userAccountControl`.

## Architecture

The Knox LDAP Service is integrated as a core gateway service. It consists of the following components:

1.  **KnoxLDAPServerManager**: Manages the lifecycle of the ApacheDS instance.
2.  **UserLookupInterceptor**: A custom ApacheDS interceptor that captures search requests. If an entry is not found in the local ApacheDS partitions, it delegates the lookup to the configured backend.
3.  **LdapBackend**: A pluggable interface for fetching user and group data. Implementations are included for reading users from a file or connecting to a remote LDAP server.
4.  **SchemaManagerFactory**: Programmatically extends the ApacheDS schema to include AD-specific attributes like `sAMAccountName`.

When a client performs an LDAP search:
1.  The request hits the embedded ApacheDS server.
2.  The `UserLookupInterceptor` intercepts the search.
3.  The interceptor checks the results of the local search.
4.  If not found, it queries the configured `LdapBackend`.
5.  Results from the backend are converted into LDAP entries and returned to the client.

## Configuration

The service is configured in `gateway-site.xml`.

### Common Properties

| Property | Default Value | Description |
| :--- | :--- | :--- |
| `gateway.ldap.enabled` | `false` | Enables or disables the embedded LDAP service. |
| `gateway.ldap.port` | `3890` | The port on which the LDAP server listens. |
| `gateway.ldap.base.dn` | `dc=proxy,dc=com` | The base DN for the LDAP server. |
| `gateway.ldap.bind.user` | N/A | Full bind DN (e.g. `uid=knox,ou=system`) that clients must authenticate as. When set together with the `gateway_ldap_bind_password` credential store alias, anonymous access is disabled. The bind DN's parent container must already exist (`ou=system` always exists; `ou=people,{base.dn}` and `ou=groups,{base.dn}` are created automatically). |
| `gateway.ldap.interceptor.names` | N/A | A comma separated list of interceptors to use. A separate interceptor configuration block will be used for each name. |
| `gateway.ldap.roles.lookup.strategy` | N/A | The LDAP roles lookup strategy (`file` or `rest`). |
| `gateway.ldap.roles.lookup.rest.api.endpoint` | N/A | The LDAP roles lookup REST API endpoint. |
| `gateway.ldap.roles.lookup.file.path` | N/A | The LDAP roles lookup file path. |

### Bind Credentials

By default the embedded LDAP server permits anonymous access. To require clients to
authenticate, set `gateway.ldap.bind.user` and store the matching password in the gateway
credential store under the `gateway_ldap_bind_password` alias. When both are present,
anonymous access is disabled and clients must bind with these credentials.

#### Relationship between the base DN and the bind user

`gateway.ldap.bind.user` is a **full DN**, not a bare username — `admin` on its own is not
valid. The bind entry is created inside the embedded directory at start-up, so its parent
container must already exist. The server creates the following containers under the
configured `gateway.ldap.base.dn`:

- `ou=people,{gateway.ldap.base.dn}`
- `ou=groups,{gateway.ldap.base.dn}`

(`ou=system` also always exists, independently of the base DN.) The bind DN must therefore
be placed under one of these containers; a DN under a container that is not created (e.g.
`ou=admins`) will fail.

For example, with:

```xml
<property>
    <name>gateway.ldap.base.dn</name>
    <value>dc=hadoop,dc=apache,dc=org</value>
</property>
```

a good bind user is a dedicated service identity under the auto-created `ou=people`
container — note how the base DN is the suffix of the bind DN:

```xml
<property>
    <name>gateway.ldap.bind.user</name>
    <value>uid=knox,ou=people,dc=hadoop,dc=apache,dc=org</value>
</property>
```

and the password is stored as:

```shell script
knoxcli.sh create-alias gateway_ldap_bind_password --value knoxsecret
```

Clients then bind with the full DN, e.g.:

```shell script
ldapsearch -x -H ldap://localhost:3890 -D "uid=knox,ou=people,dc=hadoop,dc=apache,dc=org" -w knoxsecret -b "" "(uid=admin)"
```

The bind entry is created as an `inetOrgPerson`, so either a `uid`-based RDN
(`uid=knox,...`) or a `cn`-based RDN (`cn=bind,...`) is valid. Use a dedicated identity
(e.g. `uid=knox`) rather than the built-in ApacheDS admin `uid=admin,ou=system`, which
remains available with its default credentials.

### Interceptor Types

#### Common Interceptor Properties

| Property | Default Value | Description |
| :--- | :--- | :--- |
| `gateway.ldap.interceptor.<name>.interceptorType` | N/A | The type of interceptor to use (`backend` or `duplicateuserfilter`). |

#### User Search Interceptor (`backend`)

The user search interceptor is created if the `interceptorType` configuration is set to `backend`. This interceptor forwards search queries to its configured backend.

#### Duplicate User Filter Interceptor (`duplicateuserfilter`)

The duplicate user filter interceptor ensures that each `Entry` in the results has a unique `uid`. This interceptor removes entries that have a duplicate `uid`. 

#### Disabled User Filter Interceptor (`disableduserfilter`)

The disabled user filter interceptor will recognize and filter disabled users. If the `removeDisabledUsers` property is set to `true`, users will be removed from the results. Users will have their `nsAccountLock` attribute set if `removeDisabledUsers` is set to `false`.

| Property | Default Value | Description |
| :--- | :--- | :--- |
| `gateway.ldap.interceptor.<name>.removeDisabledUsers` | `false` | Whether to remove disabled users from the results. |

#### Roles Lookup Interceptor (`rolesLookup`)

The rolesLookup interceptor is created if the `interceptorType` configuration is set to `rolesLookup`. This interceptor transforms the response entities based on the mappings provided by the Role Lookup Service. For each entity, a request will be made to lookup roles based on the user's name and group membership. These roles will replace the values in the `memberOf` attribute.

The interceptor will skip role mapping for a search request if the RolesLookupBypassControl is set to true. The control is specified using it's OID, `1.3.6.1.4.1.18060.18.0.1`. The value is a 3 byte array. This value must be base64 encoded for `ldapsearch`.

| Byte | Value | Description |
| :--- | :--- | :--- |
| Tag | 0x01 | The Boolean Tag value |
| Length | 0x03 | The length of the value in bytes |
| Bypass | 0x00 or Oxff | 0x00 corresponds to `false` and 0xff corresponds to `true |


For example, the control can be added to the `ldapsearch` cli using the `-e` option.
```shell script
ldapsearch -v -x -H ldap://localhost:3890 -b 'ou=people,DC=proxy,DC=com' -e "1.3.6.1.4.1.18060.18.0.1=AQH/" '(uid=sam*)' '*'
```

### Backend Types

#### Common Backend Properties

| Property | Default Value | Description |
| :--- | :--- | :--- |
| `gateway.ldap.interceptor.<name>.backendType` | N/A | The type of backend to use (`file` or `ldap`). |

#### File Backend (`file`)

The file backend reads user and group definitions from a JSON file. This is ideal for testing or small, static environments.

| Property | Default Value | Description |
| :--- | :--- | :--- |
| `gateway.ldap.interceptor.<name>.file` | `${GATEWAY_DATA_HOME}/ldap-users.json` | Path to the JSON data file. |

**JSON File Format Example:**
```json
{
  "users": [
    {
      "username": "guest",
      "cn": "Guest User",
      "sn": "User",
      "groups": ["users", "guests"],
      "attributes": {
        "mail": "guest@example.com"
      }
    }
  ]
}
```

#### LDAP Proxy Backend (`ldap`)

The proxy backend delegates lookups to a remote LDAP or Active Directory server.

| Property | Default Value | Description |
| :--- | :--- | :--- |
| `gateway.ldap.interceptor.<name>.url` | N/A | Remote LDAP URL (e.g., `ldap://remote-host:389`). |
| `gateway.ldap.interceptor.<name>.host` | N/A | Host of remote LDAP (used if `url` is not provided). |
| `gateway.ldap.interceptor.<name>.port` | N/A | Port of remote LDAP (used if `url` is not provided). |
| `gateway.ldap.interceptor.<name>.baseDn` | N/A | **Required**. The base DN of the LDAP proxy server. |
| `gateway.ldap.interceptor.<name>.remoteBaseDn` | N/A | **Required**. The base DN of the remote LDAP server. |
| `gateway.ldap.interceptor.<name>.systemUsername` | N/A | Bind DN for the remote server (alias: `bindDn`). |
| `gateway.ldap.interceptor.<name>.systemPassword` | N/A | Password for the bind DN (alias: `bindPassword`). |
| `gateway.ldap.interceptor.<name>.userSearchBase` | `ou=people,{remoteBaseDn}` | Base DN for user searches on the remote server. |
| `gateway.ldap.interceptor.<name>.groupSearchBase` | `ou=groups,{remoteBaseDn}` | Base DN for group searches on the remote server. |
| `gateway.ldap.interceptor.<name>.userIdentifierAttribute` | `uid` | Attribute used for user lookup (e.g., `sAMAccountName` for AD). |
| `gateway.ldap.interceptor.<name>.userObjectClass` | `inetOrgPerson` | Objectclass for identifying users in remote server (e.g., `person` for AD). |
| `gateway.ldap.interceptor.<name>.groupObjectClass` | `groupOfNames` | Objectclass for identifying groups in remote server (e.g., `group` for AD). |
| `gateway.ldap.interceptor.<name>.groupMemberAttribute` | `memberUid` | Attribute used for group membership (e.g., `member` for AD). |
| `gateway.ldap.interceptor.<name>.useMemberOf` | `false` | If `true`, use the `memberOf` attribute for efficient group lookups. |
| `gateway.ldap.interceptor.<name>.proxy.poolMaxActive` | `8` | Maximum number of active connections in the pool. |
| `gateway.ldap.interceptor.<name>.proxy.poolMaxActive` | `8` | Maximum number of active connections in the pool. |

## Active Directory (AD) Integration

The Knox LDAP Service includes several optimizations for working with Active Directory (improved in [KNOX-3277](https://issues.apache.org/jira/browse/KNOX-3277)):

- **sAMAccountName Support**: The service recognizes `sAMAccountName` in search filters, allowing seamless integration with Windows environments.
- **Efficient Group Lookups**: By setting `gateway.ldap.backend.proxy.useMemberOf` to `true`, Knox can retrieve all of a user's groups in a single query by reading the `memberOf` attribute, rather than searching all group objects.
- **Schema Extensions**: Attributes (e.g., `memberOf`, `nsAccountLock`, and AD-specific attributes `sAMAccountName` and `userAccountControl`) are programmatically added to the embedded ApacheDS schema to prevent "attribute not found" errors during proxying.
- **Case Sensitivity**: Search filters are handled to accommodate AD's case-insensitive nature for user identifiers.

## Usage Example

To configure Knox to act as an LDAP proxy for a local file and an Active Directory server:

```xml
<!-- LDAP Proxy Service Configuration -->
<property>
    <name>gateway.ldap.enabled</name>
    <value>true</value>
</property>
<property>
    <name>gateway.ldap.port</name>
    <value>389</value>
</property>
<property>
    <name>gateway.ldap.base.dn</name>
    <value>dc=proxy,dc=com</value>
</property>

<property>
    <name>gateway.ldap.interceptor.names</name>
    <value>localfile,adexample,extrenalldap,duplicatefilter,disableduserfilter,rolesLookup</value>
</property>

<property>
    <name>gateway.ldap.roles.lookup.strategy</name>
    <value>rest</value>
</property>

<property>
    <name>gateway.ldap.roles.lookup.rest.api.endpoint</name>
    <value>http://localhost:8080/auth/roles</value>
</property>

<!-- File-based LDAP backend -->
<property>
    <name>gateway.ldap.interceptor.localfile.interceptorType</name>
    <value>backend</value>
</property>
<property>
    <name>gateway.ldap.interceptor.localfile.backendType</name>
    <value>file</value>
</property>
<property>
    <name>gateway.ldap.interceptor.localfile.file</name>
    <value>${GATEWAY_DATA_HOME}/ldap-users.json</value>
</property>

<!-- LDAP backend proxy configured for Active Directory -->
<property>
    <name>gateway.ldap.interceptor.adexample.interceptorType</name>
    <value>backend</value>
</property>
<property>
    <name>gateway.ldap.interceptor.adexample.backendType</name>
    <value>ldap</value>
</property>
<property>
    <name>gateway.ldap.interceptor.adexample.url</name>
    <value>ldap://ad.example.com:389</value>
</property>
<property>
    <name>gateway.ldap.interceptor.adexample.remoteBaseDn</name>
    <value>dc=example,dc=com</value>
</property>
<property>
    <name>gateway.ldap.interceptor.adexample.systemUsername</name>
    <value>cn=ReadOnlyUser,cn=Users,dc=example,dc=com</value>
</property>
<property>
    <name>gateway.ldap.interceptor.adexample.systemPassword</name>
    <value>guest-password</value>
</property>
<property>
    <name>gateway.ldap.interceptor.adexample.userIdentifierAttribute</name>
    <value>sAMAccountName</value>
</property>
<property>
    <name>gateway.ldap.interceptor.adexample.groupMemberAttribute</name>
    <value>member</value>
</property>
<property>
    <name>gateway.ldap.interceptor.adexample.useMemberOf</name>
    <value>true</value>
</property>
<property>
    <name>gateway.ldap.interceptor.adexample.userObjectClass</name>
    <value>person</value>
</property>
<property>
    <name>gateway.ldap.interceptor.adexample.groupObjectClass</name>
    <value>group</value>
</property>

<!--
Example: Using external LDAP with authentication (supports both naming conventions)
<property>
    <name>gateway.ldap.interceptor.externalldap.backendType</name>
    <value>ldap</value>
    <description>Backend type for LDAP service. Currently supported: file, ldap. Future: jdbc, knox.</description>
</property>
<property>
    <name>gateway.ldap.interceptor.externalldap.url</name>
    <value>ldap://ldap.example.com:389</value>
    <description>LDAP server URL</description>
</property>
<property>
    <name>gateway.ldap.interceptor.externalldap.remoteBaseDn</name>
    <value>dc=example,dc=com</value>
    <description>Base DN of the remote LDAP server</description>
</property>
<property>
    <name>gateway.ldap.interceptor.externalldap.systemUsername</name>
    <value>cn=admin,dc=example,dc=com</value>
    <description>LDAP bind DN for authentication (or use bindDn)</description>
</property>
<property>
    <name>gateway.ldap.interceptor.externalldap.systemPassword</name>
    <value>secret</value>
    <description>LDAP bind password (or use bindPassword)</description>
</property>
<property>
    <name>gateway.ldap.interceptor.externalldap.userSearchBase</name>
    <value>ou=people,dc=example,dc=com</value>
    <description>Base DN for user searches on remote server (defaults to ou=people,{remoteBaseDn})</description>
</property>
<property>
    <name>gateway.ldap.interceptor.externalldap.groupSearchBase</name>
    <value>ou=groups,dc=example,dc=com</value>
    <description>Base DN for group searches on remote server (defaults to ou=groups,{remoteBaseDn})</description>
</property>
-->
<!--
Alternative: Use host and port instead of URL
<property>
    <name>gateway.ldap.interceptor.externalldap.host</name>
    <value>localhost</value>
    <description>LDAP server hostname</description>
</property>
<property>
    <name>gateway.ldap.interceptor.externalldap.port</name>
    <value>33389</value>
    <description>LDAP server port</description>
</property>
-->

<!-- Duplicate Filter Interceptor -->
<property>
    <name>gateway.ldap.interceptor.duplicatefilter.interceptorType</name>
    <value>duplicateuserfilter</value>
</property>

<!-- Disabled User Filter Interceptor -->
<property>
    <name>gateway.ldap.interceptor.disableduserfilter.interceptorType</name>
    <value>disableduserfilter</value>
</property>
<property>
    <name>gateway.ldap.interceptor.disableduserfilter.removeDisabledUsers</name>
    <value>false</value>
</property>

<!-- Roles Lookup Interceptor -->
<property>
    <name>gateway.ldap.interceptor.rolesLookup.interceptorType</name>
    <value>rolesLookup</value>
</property>

<!-- End LDAP Proxy Service Configuration -->
```

## Troubleshooting

- **Logs**: LDAP service logs can be found in `gateway.log`. Look for messages from `org.apache.knox.gateway.services.ldap`.
- **Lock Files**: If Knox crashes, an `instance.lock` file might remain in `${GATEWAY_DATA_HOME}/ldap-server/run/`. The service attempts to clean this up on startup.
- **Anonymous Access**: The embedded LDAP server allows anonymous access by default to facilitate discovery and simple binds, but backend lookups are performed using the configured `systemUsername`. To require authentication, set `gateway.ldap.bind.user` and store the corresponding password in the gateway credential store under the `gateway_ldap_bind_password` alias (e.g. `knoxcli.sh create-alias gateway_ldap_bind_password --value <password>`). Clients must then bind with those credentials (e.g. `ldapsearch -D "uid=knox,ou=system" -w <password> ...`) and anonymous queries are rejected. Note that the built-in ApacheDS admin (`uid=admin,ou=system`) remains available with its default credentials.
