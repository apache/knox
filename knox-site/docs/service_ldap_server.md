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
2.  **UserSearchInterceptor**: A custom ApacheDS interceptor that captures search requests. If an entry is not found in the local ApacheDS partitions, it delegates the lookup to the configured backend.
3.  **LdapBackend**: A pluggable interface for fetching user and group data. Implementations are included for reading users from a file or connecting to a remote LDAP server.
4.  **LDAPRolesLookupInterceptor**: An optional interceptor that maps groups to roles in search results, lists roles for group searches and resolves role DNs in `memberOf` filters (see [Roles Lookup Interceptor](#roles-lookup-interceptor-roleslookup)).
5.  **SchemaManagerFactory**: Programmatically extends the ApacheDS schema to include AD-specific attributes like `sAMAccountName`.

When a client performs an LDAP search:
1.  The request hits the embedded ApacheDS server.
2.  The `UserSearchInterceptor` intercepts the search.
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
| `gateway.ldap.bind.user` | N/A | Full bind DN (e.g. `uid=knox,dc=proxy,dc=com`) that clients must authenticate as. When set together with the `gateway_ldap_bind_password` credential store alias, anonymous access is disabled. The bind DN's parent container must already exist (`ou=system` always exists; `ou=people,{base.dn}` and `ou=groups,{base.dn}` are created automatically). |
| `gateway.ldap.interceptor.names` | N/A | A comma separated list of interceptors to use. A separate interceptor configuration block will be used for each name. |
| `gateway.ldap.roles.lookup.strategy` | N/A | The LDAP roles lookup strategy (`file` or `rest`). The strategy is used for both the user-to-roles lookup and the reverse role lookup (role-to-assignees). |
| `gateway.ldap.roles.lookup.rest.api.endpoint` | N/A | The LDAP roles lookup REST API endpoint, used to look up the roles of a user. Required for the `rest` strategy. |
| `gateway.ldap.roles.list.rest.api.endpoint` | N/A | The REST API endpoint used to list roles. Required for the `rest` strategy. |
| `gateway.ldap.roles.get.rest.api.endpoint` | N/A | The REST API endpoint used to get a single role, including its members. The role id is appended to the endpoint as a path segment (i.e., `<endpoint>/<roleId>`). Required for the `rest` strategy. |
| `gateway.ldap.roles.lookup.file.path` | N/A | The LDAP roles lookup file path. Required for the `file` strategy. |
| `gateway.ldap.ssl.enabled` | `false` | Enables LDAPS on the embedded LDAP server. |
| `gateway.ldap.ssl.keystore.path` | Gateway identity keystore | Path to the keystore holding the LDAPS server certificate. Defaults to the gateway identity keystore if not set. |
| `gateway.ldap.ssl.keystore.password.alias` | N/A | Credential store alias of the LDAPS keystore password. |
| `gateway.ldap.ssl.enabled.cipher.suites` | N/A | Comma separated list of cipher suites enabled for LDAPS. The server defaults are used if not set. |
| `gateway.ldap.max.size.limit` | 1000 | The maximum size limit of the result set returned by search requests. |
| `gateway.ldap.max.time.limit` | 60000 | The maximum time limit for search requests in milliseconds. |
| `gateway.ldap.dn.mapping.enabled` | True | Enables or disables mapping remote DNs to the LDAP server base DN. |
| `gateway.ldap.backend.password.alias.retry.attempts` | 0 | How many more times a backend password alias that is not yet in the credential store is looked up before it is left unresolved. `0` disables retries. See [Credential store aliases for backend passwords](#credential-store-aliases-for-backend-passwords). |
| `gateway.ldap.backend.password.alias.retry.interval.ms` | 1000 | Delay in milliseconds between backend password alias lookup retries. |
| `gateway.ldap.recursive.group.resolution` | False | Enables or disables retrieval of transitive group membership through recursive search queries against remote LDAP backends. |
| `gateway.ldap.recursive.group.resolution.max.depth` | 3 | Maximum depth of recursion for transitive group membership retrieval. |

NOTE: All three `gateway.ldap.roles.lookup.rest.api.endpoint`, `gateway.ldap.roles.list.rest.api.endpoint`, and `gateway.ldap.roles.get.rest.api.endpoint` properties are required when using the `rest` roles strategy.

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

Either a `uid`-based RDN (`uid=knox,...`) or a `cn`-based RDN (`cn=bind,...`) are accepted as valid Bind DNs.
Use a dedicated identity (e.g. `uid=knox`) rather than the built-in ApacheDS admin `uid=admin,ou=system`, which
remains available with its default credentials.

#### Rotating bind credentials

Knox keeps the bind dn and credentials in-memory for authentication. The bind credentials can be rotated by
changing the `gateway.ldap.bind.user` property in the gateway configuration and aliased password. The bind
user will be updated when the gateway is reloaded.

### Interceptor Types

#### Common Interceptor Properties

| Property | Default Value | Description |
| :--- | :--- | :--- |
| `gateway.ldap.interceptor.<name>.interceptorType` | N/A | The type of interceptor to use (`backend`, `duplicateuserfilter`, `disableduserfilter`, or `rolesLookup`). |

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

The rolesLookup interceptor is created if the `interceptorType` configuration is set to `rolesLookup`. It uses the Role Lookup Service, configured with `gateway.ldap.roles.lookup.strategy`, to apply roles to search requests in three ways.

**Role mapping.** For each user entry returned, a request is made to lookup roles based on the user's name and group membership. These roles replace the values in the `memberOf` attribute. Group entries are replaced with role entries named after the roles mapped to the group.

E.g.,
```shell
ldapsearch -H ldap://<ldaphost>:<ldapport> -D <bind_dn> -w <bind_password> -b 'ou=people,dc=proxy,dc=com', -s sub '(objectClass=inetOrgPerson)' '*'
```

**Role listing.** A search for all groups is answered with the roles known to the Role Lookup Service instead of the backend groups. Each role is returned as a `groupOfNames` entry with the DN `cn=<scope>:<name>,ou=groups,<base dn>`. Listed Role entries do not contain the `member` attribute because the role data is returned through the role strategy without further search requests to LDAP. A search is an all-groups search when its scope is not `base` (object) and its filter is an all-groups filter, and it is issued either from `ou=groups,<base dn>` or from the root of the directory (a base DN of at most two RDNs). A filter is an all-groups filter if it is one of:

- `(objectClass=groupOfNames)` or `(objectClass=groupOfUniqueNames)`
- `(cn=*)` or `(objectClass=*)`
- an AND filter where every child is an all-groups filter, for example `(&(objectClass=groupOfNames)(cn=*))`
- an OR filter where at least one child is an all-groups filter

A NOT filter is never an all-groups filter. Group searches with other filters, such as `(cn=level*)`, are sent to the backend and the groups in the result are mapped to roles.

E.g.,
```shell
ldapsearch -H ldap://<ldaphost>:<ldapport> -D <bind_dn> -w <bind_password> -b 'ou=groups,dc=proxy,dc=com', -s sub '(objectClass=groupOfNames)' '*'
```

**Reverse role lookup.** A search filter that uses `memberOf` with a role DN, for example `(memberOf=cn=<role>,ou=groups,dc=proxy,dc=com)`, is rewritten to match the users and groups the role is assigned to. The role is taken from the RDN value of the DN. Users assigned the role are matched by `uid` and groups assigned the role are matched by `memberOf`. The rewrite is applied to `memberOf` conditions anywhere in the filter, including inside AND, OR and NOT filters. If the role has no assignees the condition is left unchanged. If the backend does not support `memberOf` (`useMemberOf` is `false` for the [LDAP proxy backend](#ldap-proxy-backend-ldap)), the remaining `memberOf` conditions on groups are expanded by the backend into the members of the groups, up to `gateway.ldap.recursive.group.resolution.max.depth` levels of nested groups.

E.g.,
```shell
ldapsearch -H ldap://<ldaphost>:<ldapport> -D <bind_dn> -w <bind_password> -b 'ou=people,dc=proxy,dc=com', -s sub '(memberOf=cn=platform:admin,ou=groups,dc=proxy,dc=com)' '*'
```

The interceptor will skip all of the above for a search request if the RolesLookupBypassControl is set to true. The search is then passed to the backend unchanged, so group names, group entries and group DNs in `memberOf` filters are returned and used as is. The control is specified using it's OID, `1.3.6.1.4.1.18060.18.0.1`. The value is a 3 byte array. This value must be base64 encoded for `ldapsearch`.

| Byte | Value | Description |
| :--- | :--- | :--- |
| Tag | 0x01 | The Boolean Tag value |
| Length | 0x01 | The length of the value in bytes |
| Bypass | 0x00 or 0xff | 0x00 corresponds to `false` and 0xff corresponds to `true` |


For example, the control can be added to the `ldapsearch` cli using the `-e` option.
```shell script
ldapsearch -v -x -H ldap://localhost:3890 -b 'ou=people,DC=proxy,DC=com' -e "1.3.6.1.4.1.18060.18.0.1=AQH/" '(uid=sam*)' '*'
```

For example, to find the users that have the role `platform:admin`, and to list all roles:
```shell script
ldapsearch -v -x -H ldap://localhost:3890 -b 'ou=people,DC=proxy,DC=com' '(memberOf=cn=platform:admin,ou=groups,DC=proxy,DC=com)' uid
ldapsearch -v -x -H ldap://localhost:3890 -b 'ou=groups,DC=proxy,DC=com' '(objectClass=groupOfNames)' cn
```

### LDAP Backend Types

#### Common LDAP Backend Properties

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
| `gateway.ldap.interceptor.<name>.systemPassword` | N/A | Password for the bind DN (alias: `bindPassword`). May be a literal value or a credential store alias reference `S{ALIAS=...}` (see note below). |
| `gateway.ldap.interceptor.<name>.userSearchBase` | `ou=people,{remoteBaseDn}` | Base DN for user searches on the remote server. |
| `gateway.ldap.interceptor.<name>.groupSearchBase` | `ou=groups,{remoteBaseDn}` | Base DN for group searches on the remote server. |
| `gateway.ldap.interceptor.<name>.userIdentifierAttribute` | `uid` | Attribute used for user lookup (e.g., `sAMAccountName` for AD). |
| `gateway.ldap.interceptor.<name>.userObjectClass` | `inetOrgPerson` | Objectclass for identifying users in remote server (e.g., `person` for AD). |
| `gateway.ldap.interceptor.<name>.groupObjectClass` | `groupOfNames` | Objectclass for identifying groups in remote server (e.g., `group` for AD). |
| `gateway.ldap.interceptor.<name>.groupMemberAttribute` | `memberUid` | Attribute used for group membership (e.g., `member` for AD). |
| `gateway.ldap.interceptor.<name>.useMemberOf` | `false` | If `true`, use the `memberOf` attribute for efficient group lookups. |
| `gateway.ldap.interceptor.<name>.proxy.poolMaxActive` | `8` | Maximum number of active connections in the pool. |
| `gateway.ldap.interceptor.<name>.pageSize` | `1000` | Page size for search requests. |
| `gateway.ldap.interceptor.<name>.maxFilterLength` | `2000` | Maximum length of a search filter sent to the remote server. A longer filter (for example one expanded from a `memberOf` condition on a large group) is split into several searches whose results are combined. 0 means unlimited. |
| `gateway.ldap.interceptor.<name>.maxResultSetSize` | `0` | Maximum number of results to return from a search, regardless of paging. 0 means unlimited. |

NOTE: If this value is undefined and the interceptor was created by the KnoxLDAPServerManager, the KnoxLDAPServerManager will set the `gateway.ldap.interceptor.<name>.maxResultSetSize` value to be 1 greater than the proxy's `gateway.ldap.max.size.limit` configuration. This will ensure that the proxy returns a "Size limit exceeded" result if the backend has more results than the proxy's limit.

##### Credential store aliases for backend passwords

The proxy backend's password properties (`systemPassword`/`bindPassword` and `trustStorePassword`) accept a credential store alias reference of the form `S{ALIAS=my-alias}` in addition to a literal value ([KNOX-3497](https://issues.apache.org/jira/browse/KNOX-3497)). When a value is an alias reference, Knox resolves it from the gateway credential store at startup so the secret does not need to live in cleartext in `gateway-site.xml`. Literal values are used as-is. If an alias cannot be resolved it is left unchanged and an ERROR is logged, so the backend bind fails rather than silently binding with a different credential.

```bash
knoxcli.sh create-alias my_backend_bind_password --value <password>
```

```xml
<property>
    <name>gateway.ldap.interceptor.adexample.systemPassword</name>
    <value>S{ALIAS=my_backend_bind_password}</value>
</property>
```

If the alias may be created shortly after the configuration is loaded (for example when a Kubernetes Secret and the configuration referencing it are created together), set `gateway.ldap.backend.password.alias.retry.attempts` to have Knox look up a missing alias again every `gateway.ldap.backend.password.alias.retry.interval.ms` milliseconds before giving up ([KNOX-3513](https://issues.apache.org/jira/browse/KNOX-3513)). Each retry is logged at WARN; the ERROR is only logged once the retries are exhausted. All missing aliases are retried together, so the LDAP service waits at most attempts x interval while starting or reloading. The Knox CLI (`knoxcli.sh`) never retries, so commands such as `create-alias` are not held up by the missing alias.

### Role Strategy

The role strategy, selected with `gateway.ldap.roles.lookup.strategy`, is where the [Roles Lookup Interceptor](#roles-lookup-interceptor-roleslookup) gets its role data. The same strategy answers three questions:

| Question | Used for |
| :--- | :--- |
| Which roles does a user have? (`user` and the user's `groups` -> roles) | Role mapping of user and group entries |
| Which roles exist? | Role listing |
| Which users and groups is a role assigned to? (role -> users and groups) | Reverse role lookup |

A role is identified by a scope and a name. It is written as `<scope>:<name>` (for example `platform:admin`) wherever Knox shows or accepts a role, such as in `memberOf` values, role entry DNs (`cn=platform:admin,ou=groups,<base dn>`) and role listings. A role without a scope is written as just `<name>`.

#### File-based Roles Strategy (`file`)

The file strategy reads role assignments from a JSON file. It is suited to testing and small, static environments. Set `gateway.ldap.roles.lookup.strategy` to `file` and `gateway.ldap.roles.lookup.file.path` to the path of the file.

The file contains a JSON array. Each element assigns roles to one user or one group:

| Field | Description |
| :--- | :--- |
| `id` | The user name or group name the roles are assigned to. |
| `type` | `user` or `group`. |
| `roles` | The list of roles assigned. Each role has a `name` and an optional `scope`. |

```json
[
  {
    "id": "alice",
    "type": "user",
    "roles": [
      { "scope": "platform", "name": "awc-admin" }
    ]
  },
  {
    "id": "engineering",
    "type": "group",
    "roles": [
      { "scope": "ml-workspace-abc", "name": "viewer" },
      { "scope": "platform", "name": "analyst" }
    ]
  }
]
```

The entries are used as follows:

- **User to roles.** The roles of a user are the roles of every `user` entry whose `id` equals the user name, plus the roles of every `group` entry whose `id` is one of the user's groups. Duplicate roles are removed.
- **Role to assignees.** The assignees of a role are the `id` of every entry that has the role, split into users (`type` is `user`) and groups (`type` is `group`). Roles are compared as `<scope>:<name>`, ignoring case.
- **Listing roles.** The distinct `<scope>:<name>` values of all the entries. The file format does not support roles that have no assignments.

The same file is used for all three use-cases to ensure consist results. The file is read on every request, so changes take effect without restarting the gateway. If the file does not exist or cannot be parsed, the request that needs it fails with the error `Role mapping file ... does not exist` or `Error reading roles mapping file`.

#### Roles REST API contract (`rest`)

The REST strategy calls an external service. Set `gateway.ldap.roles.lookup.strategy` to `rest` and configure all three endpoints:

| Property | HTTP request | Purpose                                |
| :--- | :--- |:---------------------------------------|
| `gateway.ldap.roles.lookup.rest.api.endpoint` | `POST <endpoint>` | Get the roles of a user and/or groups. |
| `gateway.ldap.roles.list.rest.api.endpoint` | `GET <endpoint>?name=&scope=&offset=` | List roles, one page at a time.        |
| `gateway.ldap.roles.get.rest.api.endpoint` | `GET <endpoint>/<role id>` | Get one role and its members.          |

All responses must have status `200` and a JSON body. Any other status, or an empty body, makes the request fail and the failure is logged. Unknown JSON properties in responses are ignored.

##### Look up the roles of a user

Knox sends a `POST` with a JSON body (`Content-Type: application/json`) containing the username and the names of the user's groups:

```json
{
  "user_id": "alice",
  "groups": ["engineering", "analysts"]
}
```

The service responds with the roles that apply to the user and groups. `scope` may be omitted or empty for a role that has no scope.

```json
{
  "user_id": "alice",
  "roles": [
    { "scope": "platform", "name": "awc-admin" },
    { "scope": "ml-workspace-abc", "name": "viewer" }
  ]
}
```

##### List roles

Knox sends a `GET` with the header `Accept: application/json`. The query parameters are:

| Parameter | Description |
| :--- | :--- |
| `offset` | Always sent. The number of roles Knox has already retrieved, starting at `0`. |
| `name` | Only sent when Knox looks for one role (reverse role lookup). Only roles with this name should be returned. |
| `scope` | Only sent when Knox looks for one role (reverse role lookup) and the role has a scope. Only roles with this scope should be returned. |

The service responds with a page of roles and the paging information:

```json
{
  "data": [
    { "id": "role-1", "name": "admin", "scope": "platform" },
    { "id": "role-2", "name": "viewer", "scope": "workspace-abc" }
  ],
  "pagination": {
    "limit": 2,
    "offset": 0,
    "total": 5
  }
}
```

| Field | Description |
| :--- | :--- |
| `data[].id` | The unique id of the role. It is used to get the role's members. |
| `data[].name` | The role name. |
| `data[].scope` | The role scope. |
| `pagination.total` | The total number of roles that match the request. |

Knox keeps requesting pages, advancing `offset` by the number of roles it has received, until it has received `pagination.total` roles. The page size is chosen by the service. A page with no roles before `total` is reached fails the listing with `No more roles to list`.

When a single role is looked up, Knox searches the returned roles for the one whose `name` and `scope` equal the requested role, ignoring case. The service may therefore return more than the exact match, but it must include the matching role.

##### Get a role and its members

Knox sends a `GET` with the header `Accept: application/json` to the get endpoint with the role `id` appended as a path segment. The id is URL-encoded, with spaces encoded as `%20`. For example, with the endpoint `http://roles.example.com/roles/get` and the role id `role 1`, Knox requests `http://roles.example.com/roles/get/role%201`.

The service responds with the role and its members:

```json
{
  "id": "role-1",
  "name": "admin",
  "scope": "platform",
  "members": [
    { "id": "m-1", "member_type": "user", "member_id": "alice" },
    { "id": "m-2", "member_type": "group", "member_id": "engineering" }
  ]
}
```

| Field | Description |
| :--- | :--- |
| `members[].member_type` | `user` or `group` (case-insensitive). Members of any other type are ignored and logged. |
| `members[].member_id` | The user name or group name the role is assigned to. |

##### Reverse role lookup flow

To find the assignees of the role `platform:admin` (for example for `(memberOf=cn=platform:admin,ou=groups,<base dn>)`), Knox lists roles with `name=admin&scope=platform`, picks the role with the matching name and scope, and gets its members from the get endpoint. The users in the members become `uid` conditions and the groups become `memberOf` conditions of the rewritten search filter. If no matching role is listed, the role has no assignees.

To list all roles (role listing), Knox lists roles without the `name` and `scope` parameters and returns `<scope>:<name>` for every role.

## Active Directory (AD) Integration

The Knox LDAP Service includes several optimizations for working with Active Directory (improved in [KNOX-3277](https://issues.apache.org/jira/browse/KNOX-3277)):

- **sAMAccountName Support**: The service recognizes `sAMAccountName` in search filters, allowing seamless integration with Windows environments.
- **Efficient Group Lookups**: By setting `gateway.ldap.interceptor.<name>.useMemberOf` to `true`, Knox can retrieve all of a user's groups in a single query by reading the `memberOf` attribute, rather than searching all group objects.
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
    <value>localfile,adexample,duplicatefilter,disableduserfilter,rolesLookup</value>
</property>

<property>
    <name>gateway.ldap.roles.lookup.strategy</name>
    <value>rest</value>
</property>

<property>
    <name>gateway.ldap.roles.lookup.rest.api.endpoint</name>
    <value>http://localhost:8080/auth/roles/lookup</value>
</property>
<property>
    <name>gateway.ldap.roles.list.rest.api.endpoint</name>
    <value>http://localhost:8080/auth/roles</value>
</property>
<property>
    <name>gateway.ldap.roles.get.rest.api.endpoint</name>
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
