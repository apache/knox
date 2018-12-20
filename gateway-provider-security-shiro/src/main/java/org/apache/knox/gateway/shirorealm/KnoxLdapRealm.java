/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.apache.knox.gateway.shirorealm;

import org.apache.knox.gateway.GatewayMessages;
import org.apache.knox.gateway.audit.api.Action;
import org.apache.knox.gateway.audit.api.ActionOutcome;
import org.apache.knox.gateway.audit.api.AuditService;
import org.apache.knox.gateway.audit.api.AuditServiceFactory;
import org.apache.knox.gateway.audit.api.Auditor;
import org.apache.knox.gateway.audit.api.ResourceType;
import org.apache.knox.gateway.audit.log4j.audit.AuditConstants;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.shirorealm.impl.i18n.KnoxShiroMessages;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authc.credential.HashedCredentialsMatcher;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.crypto.hash.DefaultHashService;
import org.apache.shiro.crypto.hash.Hash;
import org.apache.shiro.crypto.hash.HashRequest;
import org.apache.shiro.crypto.hash.HashService;
import org.apache.shiro.realm.ldap.DefaultLdapRealm;
import org.apache.shiro.realm.ldap.LdapContextFactory;
import org.apache.shiro.realm.ldap.LdapUtils;
import org.apache.shiro.subject.MutablePrincipalCollection;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.util.StringUtils;

import javax.naming.AuthenticationException;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.PartialResultException;
import javax.naming.SizeLimitExceededException;
import javax.naming.directory.Attribute;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.Control;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.PagedResultsControl;
import javax.naming.ldap.PagedResultsResponseControl;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implementation of {@link org.apache.shiro.realm.ldap.DefaultLdapRealm} that also
 * returns each user's groups.
 * This implementation is heavily based on org.apache.isis.security.shiro.IsisLdapRealm.
 *
 * This implementation saves looked up ldap groups in Shiro Session to make them
 * easy to be looked up outside of this object
 *
 * <p>
 * Sample config for <tt>shiro.ini</tt>:
 *
 * <pre>
 * [main]
 * ldapRealm=KnoxLdapRealm
 * ldapGroupContextFactory=KnoxLdapContextFactory
 * ldapRealm.contextFactory=$ldapGroupContextFactory
 * ldapRealm.contextFactory.authenticationMechanism=simple
 * ldapRealm.contextFactory.url=ldap://localhost:33389
 * ldapRealm.userDnTemplate=uid={0},ou=people,dc=hadoop,dc=apache,dc=org
 * ldapRealm.authorizationEnabled=true
 * ldapRealm.contextFactory.systemAuthenticationMechanism=simple
 * ldapRealm.searchBase=ou=groups,dc=hadoop,dc=apache,dc=org
 * ldapRealm.groupObjectClass=groupofnames
 * ldapRealm.memberAttribute=member
 * ldapRealm.memberAttributeValueTemplate=cn={0},ou=people,dc=hadoop,dc=apache,dc=org
 * ldapRealm.contextFactory.systemUsername=uid=guest,ou=people,dc=hadoop,dc=apache,dc=org
 * ldapRealm.contextFactory.clusterName=sandbox
 * ldapRealm.contextFactory.systemPassword=S{ALIAS=ldcSystemPassword}
 * [urls]
 * **=authcBasic
 *
 * # optional mapping from physical groups to logical application roles
 * ldapRealm.rolesByGroup = \
 *    LDN_USERS: user_role,\
 *    NYK_USERS: user_role,\
 *    HKG_USERS: user_role,\
 *    GLOBAL_ADMIN: admin_role,\
 *    DEMOS: self-install_role
 *
 * ldapRealm.permissionsByRole=\
 *    user_role = *:ToDoItemsJdo:*:*,\
 *                *:ToDoItem:*:*; \
 *    self-install_role = *:ToDoItemsFixturesService:install:* ; \
 *    admin_role = *
 *
 * securityManager.realms = $ldapRealm
 *
 * </pre>
 */
public class KnoxLdapRealm extends DefaultLdapRealm {

    private static GatewayMessages LOG = MessagesFactory.get( GatewayMessages.class );
    KnoxShiroMessages ShiroLog = MessagesFactory.get( KnoxShiroMessages.class );
    private static AuditService auditService = AuditServiceFactory.getAuditService();
    private static Auditor auditor = auditService.getAuditor(
        AuditConstants.DEFAULT_AUDITOR_NAME, AuditConstants.KNOX_SERVICE_NAME,
        AuditConstants.KNOX_COMPONENT_NAME );

    private static Pattern TEMPLATE_PATTERN = Pattern.compile( "\\{(\\d+?)\\}" );
    private static String DEFAULT_PRINCIPAL_REGEX = "(.*)";
    private static final String MEMBER_SUBSTITUTION_TOKEN = "{0}";

    private static final SearchControls SUBTREE_SCOPE = new SearchControls();
    private static final SearchControls ONELEVEL_SCOPE = new SearchControls();
    private static final SearchControls OBJECT_SCOPE = new SearchControls();

    private static final String  SUBJECT_USER_ROLES = "subject.userRoles";
    private static final String  SUBJECT_USER_GROUPS = "subject.userGroups";

    private static final String  MEMBER_URL = "memberUrl";

    private static final String POSIX_GROUP = "posixGroup";

    private static final String HASHING_ALGORITHM = "SHA-256";

    static {
          SUBTREE_SCOPE.setSearchScope(SearchControls.SUBTREE_SCOPE);
          ONELEVEL_SCOPE.setSearchScope(SearchControls.ONELEVEL_SCOPE);
          OBJECT_SCOPE.setSearchScope( SearchControls.OBJECT_SCOPE );
      }

    private String searchBase;
    private String userSearchBase;
    private String principalRegex = DEFAULT_PRINCIPAL_REGEX;
    private Pattern principalPattern = Pattern.compile( DEFAULT_PRINCIPAL_REGEX );
    private String userDnTemplate = "{0}";
    private String userSearchFilter;
    private String userSearchAttributeTemplate = "{0}";
    private String userSearchScope = "subtree";

    private String groupSearchBase;

    private String groupObjectClass = "groupOfNames";

    //  typical value: member, uniqueMember, meberUrl
    private String memberAttribute = "member";

    private String groupIdAttribute = "cn";

    private String memberAttributeValuePrefix = "uid={0}";
    private String memberAttributeValueSuffix = "";

    private final Map<String,String> rolesByGroup = new LinkedHashMap<>();
    private final Map<String,List<String>> permissionsByRole = new LinkedHashMap<>();

    private boolean authorizationEnabled;

    private String userSearchAttributeName;
    private String userObjectClass = "person";

    private HashService hashService = new DefaultHashService();

    public KnoxLdapRealm() {
      HashedCredentialsMatcher credentialsMatcher = new HashedCredentialsMatcher(HASHING_ALGORITHM);
      setCredentialsMatcher(credentialsMatcher);
    }

  @Override
  //KNOX-534 overriding this method to be able to audit authentication exceptions
  protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) throws org.apache.shiro.authc.AuthenticationException {
    try {
      return super.doGetAuthenticationInfo(token);
    } catch ( org.apache.shiro.authc.AuthenticationException e ) {
      auditor.audit( Action.AUTHENTICATION , token.getPrincipal().toString(), ResourceType.PRINCIPAL, ActionOutcome.FAILURE, e.getMessage() );
      ShiroLog.failedLoginInfo(token);
      ShiroLog.failedLoginStackTrace(e);
      ShiroLog.failedLoginAttempt(e.getCause());

      throw e;
    }
  }

  /**
     * Get groups from LDAP.
     *
     * @param principals
     *            the principals of the Subject whose AuthenticationInfo should
     *            be queried from the LDAP server.
     * @param ldapContextFactory
     *            factory used to retrieve LDAP connections.
     * @return an {@link AuthorizationInfo} instance containing information
     *         retrieved from the LDAP server.
     * @throws NamingException
     *             if any LDAP errors occur during the search.
     */
    @Override
    protected AuthorizationInfo queryForAuthorizationInfo(final PrincipalCollection principals,
        final LdapContextFactory ldapContextFactory) throws NamingException {
      if (!isAuthorizationEnabled()) {
        return null;
      }
      final Set<String> roleNames = getRoles(principals, ldapContextFactory);
        SimpleAuthorizationInfo simpleAuthorizationInfo = new SimpleAuthorizationInfo(roleNames);
        Set<String> stringPermissions = permsFor(roleNames);
        simpleAuthorizationInfo.setStringPermissions(stringPermissions);
        return simpleAuthorizationInfo;
    }

    private Set<String> getRoles(PrincipalCollection principals,
        final LdapContextFactory ldapContextFactory) throws NamingException {
        final String username = (String) getAvailablePrincipal(principals);

        LdapContext systemLdapCtx = null;
        try {
            systemLdapCtx = ldapContextFactory.getSystemLdapContext();
            return rolesFor(principals, username, systemLdapCtx, ldapContextFactory);
        } catch (AuthenticationException e) {
          LOG.failedToGetSystemLdapConnection(e);
          return Collections.emptySet();
        } finally {
            LdapUtils.closeContext(systemLdapCtx);
        }
    }

    private Set<String> rolesFor(PrincipalCollection principals, final String userName, final LdapContext ldapCtx,
        final LdapContextFactory ldapContextFactory) throws NamingException {
      final Set<String> roleNames = new HashSet<>();
      final Set<String> groupNames = new HashSet<>();

      String userDn;
      if (userSearchAttributeName == null || userSearchAttributeName.isEmpty()) {
        // memberAttributeValuePrefix and memberAttributeValueSuffix were computed from memberAttributeValueTemplate
        userDn = memberAttributeValuePrefix + userName + memberAttributeValueSuffix;
      } else {
        userDn = getUserDn(userName);
      }

      // Activate paged results
      int pageSize = 100;
      int numResults = 0;
      byte[] cookie = null;
      try {
        ldapCtx.addToEnvironment(Context.REFERRAL, "ignore");

        ldapCtx.setRequestControls(new Control[]{new PagedResultsControl(pageSize, Control.NONCRITICAL)});

        do {
          // ldapsearch -h localhost -p 33389 -D uid=guest,ou=people,dc=hadoop,dc=apache,dc=org -w  guest-password
          //       -b dc=hadoop,dc=apache,dc=org -s sub '(objectclass=*)'

          NamingEnumeration<SearchResult> searchResultEnum = null;
          try {
            searchResultEnum = ldapCtx.search(
                getGroupSearchBase(),
                "objectClass=" + groupObjectClass,
                SUBTREE_SCOPE);

            while (searchResultEnum != null && searchResultEnum.hasMore()) { // searchResults contains all the groups in search scope
              numResults++;
              final SearchResult group = searchResultEnum.next();
              addRoleIfMember(userDn, group, roleNames, groupNames, ldapContextFactory);
            }
          } catch (PartialResultException e) {
            LOG.ignoringPartialResultException();
          } finally {
            if (searchResultEnum != null) {
              searchResultEnum.close();
            }
          }

          // Examine the paged results control response
          Control[] controls = ldapCtx.getResponseControls();
          if (controls != null) {
            for (Control control : controls) {
              if (control instanceof PagedResultsResponseControl) {
                PagedResultsResponseControl prrc = (PagedResultsResponseControl) control;
                cookie = prrc.getCookie();
              }
            }
          }

          // Re-activate paged results
          ldapCtx.setRequestControls(new Control[]{new PagedResultsControl(pageSize, cookie, Control.CRITICAL)});
        } while (cookie != null);
      } catch (SizeLimitExceededException e) {
        LOG.sizeLimitExceededOnlyRetrieved(numResults);
      } catch(IOException e) {
        LOG.unableToSetupPagedResults();
      }

      // save role names and group names in session so that they can be easily looked up outside of this object
      SecurityUtils.getSubject().getSession().setAttribute(SUBJECT_USER_ROLES, roleNames);
      SecurityUtils.getSubject().getSession().setAttribute(SUBJECT_USER_GROUPS, groupNames);
      if (!groupNames.isEmpty() && (principals instanceof MutablePrincipalCollection)) {
        ((MutablePrincipalCollection)principals).addAll(groupNames, getName());
      }
      LOG.lookedUpUserRoles(roleNames, userName);

      return roleNames;
    }

  private void addRoleIfMember(final String userDn, final SearchResult group,
      final Set<String> roleNames, final Set<String> groupNames,
      final LdapContextFactory ldapContextFactory) throws NamingException {

    NamingEnumeration<? extends Attribute> attributeEnum = null;
    NamingEnumeration<?> e = null;
    try {
      LdapName userLdapDn = new LdapName(userDn);
      Attribute attribute = group.getAttributes().get(getGroupIdAttribute());
      String groupName = attribute.get().toString();

      attributeEnum = group
          .getAttributes().getAll();
      while (attributeEnum.hasMore()) {
        final Attribute attr = attributeEnum.next();
        if (!memberAttribute.equalsIgnoreCase(attr.getID())) {
          continue;
        }
        e = attr.getAll();
        while (e.hasMore()) {
          String attrValue = e.next().toString();
          if (memberAttribute.equalsIgnoreCase(MEMBER_URL)) {
            boolean dynamicGroupMember = isUserMemberOfDynamicGroup(userLdapDn,
                attrValue, // memberUrl value
                ldapContextFactory);
            if (dynamicGroupMember) {
              groupNames.add(groupName);
              String roleName = roleNameFor(groupName);
              if (roleName != null) {
                roleNames.add(roleName);
              } else {
                roleNames.add(groupName);
              }
            }
          } else {
            if (groupObjectClass.equalsIgnoreCase(POSIX_GROUP)){
              attrValue = memberAttributeValuePrefix + attrValue + memberAttributeValueSuffix;
            }
            if (userLdapDn.equals(new LdapName(attrValue))) {
              groupNames.add(groupName);
              String roleName = roleNameFor(groupName);
              if (roleName != null) {
                roleNames.add(roleName);
              } else {
                roleNames.add(groupName);
              }
              break;
            }
          }
        }
      }
    }
    finally {
      try {
        if (attributeEnum != null) {
          attributeEnum.close();
        }
      }
      finally {
        if (e != null) {
          e.close();
        }
      }
    }
  }

    private String roleNameFor(String groupName) {
        return !rolesByGroup.isEmpty() ? rolesByGroup.get(groupName) : groupName;
    }


    private Set<String> permsFor(Set<String> roleNames) {
        Set<String> perms = new LinkedHashSet<>(); // preserve order
        for(String role: roleNames) {
            List<String> permsForRole = permissionsByRole.get(role);
            if(permsForRole != null) {
                perms.addAll(permsForRole);
            }
        }
        return perms;
    }

    public String getSearchBase() {
        return searchBase;
    }

    public void setSearchBase(String searchBase) {
      this.searchBase = searchBase;
    }

    public String getUserSearchBase() {
      return (userSearchBase != null && !userSearchBase.isEmpty()) ?
          userSearchBase : searchBase;
    }

    public void setUserSearchBase(String userSearchBase) {
      this.userSearchBase = userSearchBase;
    }

    public String getGroupSearchBase() {
      return (groupSearchBase != null && !groupSearchBase.isEmpty()) ?
          groupSearchBase : searchBase;
    }

    public void setGroupSearchBase(String groupSearchBase) {
      this.groupSearchBase = groupSearchBase;
    }

    public String getGroupObjectClass() {
      return groupObjectClass;
    }

    public void setGroupObjectClass(String groupObjectClassAttribute) {
        this.groupObjectClass = groupObjectClassAttribute;
    }

    public String getMemberAttribute() {
      return memberAttribute;
    }

    public void setMemberAttribute(String memberAttribute) {
        this.memberAttribute = memberAttribute;
    }

    public String getGroupIdAttribute() {
      return groupIdAttribute;
    }

    public void setGroupIdAttribute(String groupIdAttribute) {
        this.groupIdAttribute = groupIdAttribute;
    }

    public void setMemberAttributeValueTemplate(String template) {
        if (!StringUtils.hasText(template)) {
            String msg = "User DN template cannot be null or empty.";
            throw new IllegalArgumentException(msg);
        }
        int index = template.indexOf(MEMBER_SUBSTITUTION_TOKEN);
        if (index < 0) {
            String msg = "Member attribute value template must contain the '" +
                    MEMBER_SUBSTITUTION_TOKEN + "' replacement token to understand how to " +
                    "parse the group members.";
            throw new IllegalArgumentException(msg);
        }
        String prefix = template.substring(0, index);
        String suffix = template.substring(prefix.length() + MEMBER_SUBSTITUTION_TOKEN.length());
        this.memberAttributeValuePrefix = prefix;
        this.memberAttributeValueSuffix = suffix;
    }

    public void setRolesByGroup(Map<String, String> rolesByGroup) {
        this.rolesByGroup.putAll(rolesByGroup);
    }

    public void setPermissionsByRole(String permissionsByRoleStr) {
        permissionsByRole.putAll(parsePermissionByRoleString(permissionsByRoleStr));
    }

    public boolean isAuthorizationEnabled() {
      return authorizationEnabled;
    }

    public void setAuthorizationEnabled(boolean authorizationEnabled) {
      this.authorizationEnabled = authorizationEnabled;
    }

    public String getUserSearchAttributeName() {
        return userSearchAttributeName;
    }

    public void setUserSearchAttributeName(String userSearchAttributeName) {
      if (userSearchAttributeName != null) {
        userSearchAttributeName = userSearchAttributeName.trim();
      }
      this.userSearchAttributeName = userSearchAttributeName;
    }

    public String getUserObjectClass() {
      return userObjectClass;
    }

    public void setUserObjectClass(String userObjectClass) {
        this.userObjectClass = userObjectClass;
    }

    private Map<String, List<String>> parsePermissionByRoleString(String permissionsByRoleStr) {
      Map<String,List<String>> perms = new HashMap<>();

      // split by semicolon ; then by eq = then by  comma ,
      StringTokenizer stSem = new StringTokenizer(permissionsByRoleStr, ";");
      while (stSem.hasMoreTokens()) {
        String roleAndPerm = stSem.nextToken();
        StringTokenizer stEq = new StringTokenizer(roleAndPerm, "=");
        if (stEq.countTokens() != 2) {
          continue;
        }
        String role = stEq.nextToken().trim();
        String perm = stEq.nextToken().trim();
        StringTokenizer stCom = new StringTokenizer(perm, ",");
        List<String> permList = new ArrayList<>();
        while (stCom.hasMoreTokens()) {
          permList.add(stCom.nextToken().trim());
        }
        perms.put(role,  permList);
      }
      return perms;
  }

  boolean isUserMemberOfDynamicGroup(LdapName userLdapDn, String memberUrl,
      final LdapContextFactory ldapContextFactory) throws NamingException {

    // ldap://host:port/dn?attributes?scope?filter?extensions

    boolean member = false;

    if (memberUrl == null) {
      return false;
    }
    String[] tokens = memberUrl.split("\\?");
    if (tokens.length < 4) {
      return false;
    }

    String searchBaseString = tokens[0]
        .substring(tokens[0].lastIndexOf('/') + 1);
    String searchScope = tokens[2];
    String searchFilter = tokens[3];

    LdapName searchBaseDn = new LdapName(searchBaseString);

    // do scope test
    if ("base".equalsIgnoreCase(searchScope)) {
      return false;
    }
    if (!userLdapDn.toString().endsWith(searchBaseDn.toString())) {
      return false;
    }
    if ("one".equalsIgnoreCase(searchScope)
        && (userLdapDn.size() != searchBaseDn.size() - 1)) {
      return false;
    }
    // search for the filter, substituting base with userDn
    // search for base_dn=userDn, scope=base, filter=filter
    LdapContext systemLdapCtx;
    systemLdapCtx = ldapContextFactory.getSystemLdapContext();
    NamingEnumeration<SearchResult> searchResultEnum = null;
    try {
      searchResultEnum = systemLdapCtx
        .search(userLdapDn, searchFilter,
            "sub".equalsIgnoreCase(searchScope) ? SUBTREE_SCOPE
                : ONELEVEL_SCOPE);
      if (searchResultEnum.hasMore()) {
        return true;
      }
    }
    finally {
        try {
          if (searchResultEnum != null) {
            searchResultEnum.close();
          }
        }
        finally {
          LdapUtils.closeContext(systemLdapCtx);
        }
    }
    return member;
  }

  public String getPrincipalRegex() {
    return principalRegex;
  }

  public void setPrincipalRegex( String regex ) {
    if( regex == null || regex.trim().isEmpty() ) {
      principalPattern = Pattern.compile( DEFAULT_PRINCIPAL_REGEX );
      principalRegex = DEFAULT_PRINCIPAL_REGEX;
    } else {
      regex = regex.trim();
      principalPattern = Pattern.compile( regex );
      principalRegex = regex;
    }
  }

  public String getUserSearchAttributeTemplate() {
    return userSearchAttributeTemplate;
  }

  public void setUserSearchAttributeTemplate( final String template ) {
    this.userSearchAttributeTemplate = ( template == null ? null : template.trim() );
  }

  public String getUserSearchFilter() {
    return userSearchFilter;
  }

  public void setUserSearchFilter( final String filter ) {
    this.userSearchFilter = ( filter == null ? null : filter.trim() );
  }

  public String getUserSearchScope() {
    return userSearchScope;
  }

  public void setUserSearchScope( final String scope ) {
    this.userSearchScope = ( scope == null ? null : scope.trim().toLowerCase(Locale.ROOT) );
  }

  private SearchControls getUserSearchControls() {
    SearchControls searchControls = SUBTREE_SCOPE;
    if ( "onelevel".equalsIgnoreCase( userSearchScope ) ) {
      searchControls = ONELEVEL_SCOPE;
    } else if ( "object".equalsIgnoreCase( userSearchScope ) ) {
      searchControls = OBJECT_SCOPE;
    }
    return searchControls;
  }

  @Override
  public void setUserDnTemplate( final String template ) throws IllegalArgumentException {
    userDnTemplate = template;
  }

  private Matcher matchPrincipal( final String principal ) {
    Matcher matchedPrincipal = principalPattern.matcher( principal );
    if( !matchedPrincipal.matches() ) {
      throw new IllegalArgumentException( "Principal " + principal + " does not match " + principalRegex );
    }
    return matchedPrincipal;
  }

  /**
     * Returns the LDAP User Distinguished Name (DN) to use when acquiring an
     * {@link javax.naming.ldap.LdapContext LdapContext} from the {@link LdapContextFactory}.
     *
     * If the the {@link #getUserDnTemplate() userDnTemplate} property has been set, this implementation will construct
     * the User DN by substituting the specified {@code principal} into the configured template.  If the
     * {@link #getUserDnTemplate() userDnTemplate} has not been set, the method argument will be returned directly
     * (indicating that the submitted authentication token principal <em>is</em> the User DN).
     *
     * @param principal the principal to substitute into the configured {@link #getUserDnTemplate() userDnTemplate}.
     * @return the constructed User DN to use at runtime when acquiring an {@link javax.naming.ldap.LdapContext}.
     * @throws IllegalArgumentException if the method argument is null or empty
     * @throws IllegalStateException    if the {@link #getUserDnTemplate userDnTemplate} has not been set.
     * @see LdapContextFactory#getLdapContext(Object, Object)
     */
    @Override
    protected String getUserDn( final String principal ) throws IllegalArgumentException, IllegalStateException {
      String userDn;
      Matcher matchedPrincipal = matchPrincipal( principal );
      String userSearchBase = getUserSearchBase();
      String userSearchAttributeName = getUserSearchAttributeName();

      // If not searching use the userDnTemplate and return.
      if ( ( userSearchBase == null || userSearchBase.isEmpty() ) ||
          ( userSearchAttributeName == null &&
              userSearchFilter == null &&
              !"object".equalsIgnoreCase( userSearchScope ) ) ) {
        userDn = expandTemplate( userDnTemplate, matchedPrincipal );
        LOG.computedUserDn( userDn, principal );
        return userDn;
      }

      // Create the searchBase and searchFilter from config.
      String searchBase = expandTemplate( getUserSearchBase(), matchedPrincipal );
      String searchFilter;
      if ( userSearchFilter == null ) {
        if ( userSearchAttributeName == null ) {
          searchFilter = String.format( Locale.ROOT, "(objectclass=%1$s)", getUserObjectClass() );
        } else {
          searchFilter = String.format( Locale.ROOT,
              "(&(objectclass=%1$s)(%2$s=%3$s))",
              getUserObjectClass(),
              userSearchAttributeName,
              expandTemplate( getUserSearchAttributeTemplate(), matchedPrincipal ) );
        }
      } else {
        searchFilter = expandTemplate( userSearchFilter, matchedPrincipal );
      }
      SearchControls searchControls = getUserSearchControls();

      // Search for userDn and return.
      LdapContext systemLdapCtx = null;
      NamingEnumeration<SearchResult> searchResultEnum = null;
      try {
        systemLdapCtx = getContextFactory().getSystemLdapContext();
        LOG.searchBaseFilterScope(searchBase, searchFilter, userSearchScope);
        searchResultEnum = systemLdapCtx.search( searchBase, searchFilter, searchControls );
        // SearchResults contains all the entries in search scope
        if (searchResultEnum.hasMore()) {
          SearchResult searchResult = searchResultEnum.next();
          userDn = searchResult.getNameInNamespace();
          LOG.searchedAndFoundUserDn(userDn, principal);
          return userDn;
        } else {
          throw new IllegalArgumentException("Illegal principal name: " + principal);
        }
      } catch (AuthenticationException e) {
        LOG.failedToGetSystemLdapConnection(e);
        throw new IllegalArgumentException("Illegal principal name: " + principal, e);
      } catch (NamingException e) {
        throw new IllegalArgumentException("Hit NamingException", e);
      } finally {
        try {
          if (searchResultEnum != null) {
            searchResultEnum.close();
          }
        } catch (NamingException e) {
          // Ignore exception on close.
        }
        finally {
          LdapUtils.closeContext(systemLdapCtx);
        }
      }
    }

    @Override
    protected AuthenticationInfo createAuthenticationInfo(AuthenticationToken token, Object ldapPrincipal, Object ldapCredentials, LdapContext ldapContext) throws NamingException {
      HashRequest.Builder builder = new HashRequest.Builder();
      Hash credentialsHash = hashService.computeHash(builder.setSource(token.getCredentials()).setAlgorithmName(HASHING_ALGORITHM).build());
      return new SimpleAuthenticationInfo(token.getPrincipal(), credentialsHash.toHex(), credentialsHash.getSalt(), getName());
    }

  private static String expandTemplate( final String template, final Matcher input ) {
    String output = template;
    Matcher matcher = TEMPLATE_PATTERN.matcher( output );
    while( matcher.find() ) {
      String lookupStr = matcher.group( 1 );
      int lookupIndex = Integer.parseInt( lookupStr );
      String lookupValue = input.group( lookupIndex );
      output = matcher.replaceFirst( lookupValue == null ? "" : lookupValue );
      matcher = TEMPLATE_PATTERN.matcher( output );
    }
    return output;
  }

}
