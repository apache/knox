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

import java.util.LinkedHashSet;
import java.util.Set;
import org.apache.knox.gateway.GatewayMessages;
import org.apache.knox.gateway.audit.api.Action;
import org.apache.knox.gateway.audit.api.ActionOutcome;
import org.apache.knox.gateway.audit.api.ResourceType;
import org.apache.knox.gateway.audit.api.AuditService;
import org.apache.knox.gateway.audit.api.AuditServiceFactory;
import org.apache.knox.gateway.audit.api.Auditor;
import org.apache.knox.gateway.audit.log4j.audit.AuditConstants;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.shirorealm.impl.i18n.KnoxShiroMessages;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.AuthenticationException;

import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.crypto.hash.DefaultHashService;
import org.apache.shiro.crypto.hash.Hash;
import org.apache.shiro.crypto.hash.HashRequest;
import org.apache.shiro.crypto.hash.HashService;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.authc.credential.HashedCredentialsMatcher;
import org.jvnet.libpam.PAM;
import org.jvnet.libpam.PAMException;
import org.jvnet.libpam.UnixUser;

/**
 * A Unix-style
 * <a href="http://www.kernel.org/pub/linux/libs/pam/index.html">PAM</a>
 * {@link org.apache.shiro.realm.Realm Realm} that uses
 * <a href="https://github.com/kohsuke/libpam4j">libpam4j</a> to interface with
 * the PAM system libraries.
 * <p>
 * This is a single Shiro {@code Realm} that interfaces with the OS's
 * {@code PAM} subsystem which itself can be connected to several authentication
 * methods (unix-crypt,Samba, LDAP, etc.)
 * <p>
 * This {@code Realm} can also take part in Shiro's Pluggable Realms concept.
 * <p>
 * Using a {@code KnoxPamRealm} requires a PAM {@code service} name. This is the
 * name of the file under {@code /etc/pam.d} that is used to initialise and
 * configure the PAM subsystem. Normally, this file reflects the application
 * using it. For example {@code gdm}, {@code su}, etc. There is no default value
 * for this property.
 * <p>
 * For example, defining this realm in Shiro .ini:
 *
 * <pre>
 * [main]
 * pamRealm = org.apache.shiro.realm.libpam4j.KnoxPamRealm
 * pamRealm.service = [ knox-pam-ldap-service | knox-pam-os-service | knox-pam-winbind-service ]
 * [urls]
 * **=authcBasic
 * </pre>
 *
 */
public class KnoxPamRealm extends AuthorizingRealm {
  private static final String HASHING_ALGORITHM = "SHA-256";
  private static final String SUBJECT_USER_ROLES = "subject.userRoles";
  private static final String SUBJECT_USER_GROUPS = "subject.userGroups";

  private static final AuditService auditService = AuditServiceFactory.getAuditService();
  private static final Auditor auditor = auditService.getAuditor(AuditConstants.DEFAULT_AUDITOR_NAME,
      AuditConstants.KNOX_SERVICE_NAME, AuditConstants.KNOX_COMPONENT_NAME);

  private final HashService hashService = new DefaultHashService();
  private final KnoxShiroMessages shiroLog = MessagesFactory.get(KnoxShiroMessages.class);
  private final GatewayMessages gatewayLog = MessagesFactory.get(GatewayMessages.class);

  private String service;

  public KnoxPamRealm() {
    HashedCredentialsMatcher credentialsMatcher = new HashedCredentialsMatcher(HASHING_ALGORITHM);
    setCredentialsMatcher(credentialsMatcher);
  }

  public void setService(String service) {
    this.service = service;
  }

  public String getService() {
    return this.service;
  }

  @Override
  protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
    Set<String> roles = new LinkedHashSet<>();

    UnixUserPrincipal user = principals.oneByType(UnixUserPrincipal.class);
    if (user != null) {
      roles.addAll(user.getUnixUser().getGroups());
    }
    SecurityUtils.getSubject().getSession().setAttribute(SUBJECT_USER_ROLES, roles);
    SecurityUtils.getSubject().getSession().setAttribute(SUBJECT_USER_GROUPS, roles);

    /* Coverity Scan CID 1361682 */
    String userName = null;

    if (user != null) {
      userName = user.getName();
    }

    gatewayLog.lookedUpUserRoles(roles, userName);
    return new SimpleAuthorizationInfo(roles);
  }

  @Override
  protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token)
      throws AuthenticationException {
    PAM pam = null;
    UnixUser user = null;
    try {
      pam = new PAM(this.getService());
      UsernamePasswordToken upToken = (UsernamePasswordToken) token;
      user = pam.authenticate(upToken.getUsername(), new String(upToken.getPassword()));
    } catch (PAMException e) {
      handleAuthFailure(token, e.getMessage(), e);
    } finally {
      if(pam != null) {
        pam.dispose();
      }
    }

    HashRequest hashRequest = new HashRequest.Builder()
                                  .setSource(token.getCredentials())
                                  .setAlgorithmName(HASHING_ALGORITHM)
                                  .build();
    Hash credentialsHash = hashService.computeHash(hashRequest);

    /* Coverity Scan CID 1361684 */
    if (credentialsHash == null) {
      handleAuthFailure(token, "Failed to compute hash", null);
    }
    return new SimpleAuthenticationInfo(new UnixUserPrincipal(user), credentialsHash.toHex(),
        credentialsHash.getSalt(), getName());
  }

  private void handleAuthFailure(AuthenticationToken token, String errorMessage, Exception e) {
    auditor.audit(Action.AUTHENTICATION, token.getPrincipal().toString(),
        ResourceType.PRINCIPAL, ActionOutcome.FAILURE, errorMessage);
    shiroLog.failedLoginInfo(token);

    if (e != null) {
      shiroLog.failedLoginAttempt(e.getCause());
      throw new AuthenticationException(e);
    }

    throw new AuthenticationException(errorMessage);
  }
}
