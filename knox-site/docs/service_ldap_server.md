# Knox LDAP Service

The Knox LDAP Service provides an embedded LDAP server within the Knox Gateway. It acts as a pluggable LDAP proxy or facade, allowing Knox to expose a standard LDAP interface to clients while fetching user and group information from various backends.

## Overview

Introduced in [KNOX-3247](https://issues.apache.org/jira/browse/KNOX-3247), the Knox LDAP Service leverages Apache Directory Server (ApacheDS) to provide a lightweight, embedded LDAP server. Its primary goal is to provide a consistent LDAP interface for authentication and group lookups, even when the underlying identity store is not a traditional LDAP server or is a remote server that requires proxying.

Key features include:
- **Pluggable Backends**: Support for different data sources (JSON files, remote LDAP/AD).
- **Embedded Server**: No need for an external LDAP server for simple use cases or testing.
- **Active Directory Integration**: Optimized for proxying to AD with support for `sAMAccountName`.

## Architecture

The Knox LDAP Service is integrated as a core gateway service. It consists of the following components:

1.  **KnoxLDAPServerManager**: Manages the lifecycle of the ApacheDS instance.
2.  **GroupLookupInterceptor**: A custom ApacheDS interceptor that captures search requests. If an entry is not found in the local ApacheDS partitions, it delegates the lookup to the configured backend.
3.  **LdapBackend**: A pluggable interface for fetching user and group data.
4.  **SchemaManagerFactory**: Programmatically extends the ApacheDS schema to include AD-specific attributes like `sAMAccountName`.

When a client performs an LDAP search:
1.  The request hits the embedded ApacheDS server.
2.  The `GroupLookupInterceptor` intercepts the search.
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
| `gateway.ldap.backend.type` | `file` | The type of backend to use (`file` or `proxy`). |

### Backend Types

#### File Backend (`file`)

The file backend reads user and group definitions from a JSON file. This is ideal for testing or small, static environments.

| Property | Default Value | Description |
| :--- | :--- | :--- |
| `gateway.ldap.backend.data.file` | `${GATEWAY_DATA_HOME}/ldap-users.json` | Path to the JSON data file. |

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

#### Proxy Backend (`proxy`)

The proxy backend delegates lookups to a remote LDAP or Active Directory server.

| Property | Default Value | Description |
| :--- | :--- | :--- |
| `gateway.ldap.backend.proxy.url` | N/A | Remote LDAP URL (e.g., `ldap://remote-host:389`). |
| `gateway.ldap.backend.proxy.host` | N/A | Host of remote LDAP (used if `url` is not provided). |
| `gateway.ldap.backend.proxy.port` | N/A | Port of remote LDAP (used if `url` is not provided). |
| `gateway.ldap.backend.proxy.remoteBaseDn` | N/A | **Required**. The base DN of the remote LDAP server. |
| `gateway.ldap.backend.proxy.systemUsername` | N/A | Bind DN for the remote server (alias: `bindDn`). |
| `gateway.ldap.backend.proxy.systemPassword` | N/A | Password for the bind DN (alias: `bindPassword`). |
| `gateway.ldap.backend.proxy.userSearchBase` | `ou=people,{remoteBaseDn}` | Base DN for user searches on the remote server. |
| `gateway.ldap.backend.proxy.groupSearchBase` | `ou=groups,{remoteBaseDn}` | Base DN for group searches on the remote server. |
| `gateway.ldap.backend.proxy.userIdentifierAttribute` | `uid` | Attribute used for user lookup (e.g., `sAMAccountName` for AD). |
| `gateway.ldap.backend.proxy.groupMemberAttribute` | `memberUid` | Attribute used for group membership (e.g., `member` for AD). |
| `gateway.ldap.backend.proxy.useMemberOf` | `false` | If `true`, use the `memberOf` attribute for efficient group lookups. |
| `gateway.ldap.backend.proxy.poolMaxActive` | `8` | Maximum number of active connections in the pool. |

## Active Directory (AD) Integration

The Knox LDAP Service includes several optimizations for working with Active Directory (improved in [KNOX-3277](https://issues.apache.org/jira/browse/KNOX-3277)):

- **sAMAccountName Support**: The service recognizes `sAMAccountName` in search filters, allowing seamless integration with Windows environments.
- **Efficient Group Lookups**: By setting `gateway.ldap.backend.proxy.useMemberOf` to `true`, Knox can retrieve all of a user's groups in a single query by reading the `memberOf` attribute, rather than searching all group objects.
- **Schema Extensions**: AD-specific attributes (`memberOf`, `sAMAccountName`) are programmatically added to the embedded ApacheDS schema to prevent "attribute not found" errors during proxying.
- **Case Sensitivity**: Search filters are handled to accommodate AD's case-insensitive nature for user identifiers.

## Usage Example (AD Proxy)

To configure Knox to act as an LDAP proxy for an Active Directory server:

```xml
<property>
    <name>gateway.ldap.enabled</name>
    <value>true</value>
</property>
<property>
    <name>gateway.ldap.port</name>
    <value>389</value>
</property>
<property>
    <name>gateway.ldap.backend.type</name>
    <value>proxy</value>
</property>
<property>
    <name>gateway.ldap.backend.proxy.url</name>
    <value>ldap://ad.example.com:389</value>
</property>
<property>
    <name>gateway.ldap.backend.proxy.remoteBaseDn</name>
    <value>dc=example,dc=com</value>
</property>
<property>
    <name>gateway.ldap.backend.proxy.systemUsername</name>
    <value>CN=ReadOnlyUser,CN=Users,DC=example,DC=com</value>
</property>
<property>
    <name>gateway.ldap.backend.proxy.systemPassword</name>
    <value>secret-password</value>
</property>
<property>
    <name>gateway.ldap.backend.proxy.userIdentifierAttribute</name>
    <value>sAMAccountName</value>
</property>
<property>
    <name>gateway.ldap.backend.proxy.groupMemberAttribute</name>
    <value>member</value>
</property>
<property>
    <name>gateway.ldap.backend.proxy.useMemberOf</name>
    <value>true</value>
</property>
```

## Troubleshooting

- **Logs**: LDAP service logs can be found in `gateway.log`. Look for messages from `org.apache.knox.gateway.services.ldap`.
- **Lock Files**: If Knox crashes, an `instance.lock` file might remain in `${GATEWAY_DATA_HOME}/ldap-server/run/`. The service attempts to clean this up on startup.
- **Anonymous Access**: The embedded LDAP server allows anonymous access by default to facilitate discovery and simple binds, but backend lookups are performed using the configured `systemUsername`.
