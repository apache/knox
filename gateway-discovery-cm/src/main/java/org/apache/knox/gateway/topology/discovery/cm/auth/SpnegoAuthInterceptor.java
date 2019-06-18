/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.topology.discovery.cm.auth;

import com.squareup.okhttp.Authenticator;
import com.squareup.okhttp.Interceptor;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.Oid;

import javax.security.auth.Subject;
import javax.security.auth.login.LoginException;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.UnknownHostException;
import java.security.Principal;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Base64;
import java.util.Locale;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

import static org.apache.http.HttpHeaders.AUTHORIZATION;
import static org.apache.http.HttpHeaders.WWW_AUTHENTICATE;
import static org.ietf.jgss.GSSContext.INDEFINITE_LIFETIME;
import static org.ietf.jgss.GSSCredential.DEFAULT_LIFETIME;
import static org.ietf.jgss.GSSCredential.INITIATE_ONLY;
import static org.ietf.jgss.GSSName.NT_HOSTBASED_SERVICE;
import static org.ietf.jgss.GSSName.NT_USER_NAME;

public class SpnegoAuthInterceptor implements Interceptor, Authenticator {

  private static final String NEGOTIATE = "Negotiate";

  private static final GSSManager GSS_MANAGER = GSSManager.getInstance();

  private static final Oid SPNEGO_OID   = createOid("1.3.6.1.5.5.2");
  private static final Oid KERBEROS_OID = createOid("1.2.840.113554.1.2.2");

  private static final String DEFAULT_REMOTE_SERVICE_NAME = "HTTP";

  private static final int CREDENTIAL_EXPIRATION_THRESHOLD = 60; // seconds

  private final String remoteServiceName;
  private final boolean useCanonicalHostname;

  private Subject subject;

  private GSSCredentialSession credentialSession;

  public SpnegoAuthInterceptor(Subject subject) {
    this(subject, DEFAULT_REMOTE_SERVICE_NAME);
  }

  public SpnegoAuthInterceptor(Subject subject,
                               String  remoteServiceName) {
    this(subject, remoteServiceName, true);
  }

  public SpnegoAuthInterceptor(Subject subject,
                               String  remoteServiceName,
                               boolean useCanonicalHostname) {
    this.subject = subject;
    this.remoteServiceName = remoteServiceName;
    this.useCanonicalHostname = useCanonicalHostname;
  }

  @Override
  public Response intercept(Chain chain) throws IOException {
    try {
      return chain.proceed(authenticate(chain.request()));
    } catch (Exception ignored) {
      return chain.proceed(chain.request());
    }
  }

  private static boolean isNegotiate(String value) {
    String[] split = value.split("\\s+");
    return (split.length == 2) && split[1].equalsIgnoreCase(NEGOTIATE);
  }

  @Override
  public Request authenticate(Proxy proxy, Response response) throws IOException {
    // If already attempted or not challenged for Kerberos, then skip this attempt
    if (response.request().headers(AUTHORIZATION).stream().anyMatch(SpnegoAuthInterceptor::isNegotiate) ||
        response.headers(WWW_AUTHENTICATE).stream().noneMatch(SpnegoAuthInterceptor::isNegotiate)) {
      return null;
    }

    return authenticate(response.request());
  }

  @Override
  public Request authenticateProxy(Proxy proxy, Response response) throws IOException {
    return null; // Not needed
  }

  private Request authenticate(Request request) {
    String principal = defineServicePrincipal(remoteServiceName, request.url().getHost(), useCanonicalHostname);
    byte[] token = generateToken(principal);

    String credential = format(Locale.getDefault(), "%s %s", NEGOTIATE, Base64.getEncoder().encodeToString(token));
    return request.newBuilder()
                  .header(AUTHORIZATION, credential)
                  .build();
  }

  private byte[] generateToken(String servicePrincipal) {
    GSSContext context = null;
    try {
      GSSCredentialSession GSSCredentialSession = getCredentialSession();
      context = doAs(subject, () -> {
        GSSContext result = GSS_MANAGER.createContext(GSS_MANAGER.createName(servicePrincipal, NT_HOSTBASED_SERVICE),
                            SPNEGO_OID,
                            GSSCredentialSession.getClientCredential(),
                            INDEFINITE_LIFETIME);
        result.requestMutualAuth(true);
        result.requestConf(true);
        result.requestInteg(true);
        result.requestCredDeleg(false);
        return result;
      });

      byte[] token = context.initSecContext(new byte[0], 0, 0);
      if (token == null) {
        throw new LoginException("No token generated from GSS context");
      }
      return token;
    } catch (GSSException | LoginException e) {
      throw new RuntimeException(format(Locale.getDefault(), "Kerberos error for [%s]: %s", servicePrincipal, e.getMessage()), e);
    } finally {
      try {
        if (context != null) {
          context.dispose();
        }
      } catch (GSSException ignored) {
      }
    }
  }

  private synchronized GSSCredentialSession getCredentialSession() throws GSSException {
    if ((credentialSession == null) || credentialSession.needsRefresh()) {
      credentialSession = createCredentialSession();
    }
    return credentialSession;
  }

  private GSSCredentialSession createCredentialSession() throws GSSException {
    Principal clientPrincipal = subject.getPrincipals().iterator().next();
    GSSCredential clientCredential =
        doAs(subject,
            () -> GSS_MANAGER.createCredential(GSS_MANAGER.createName(clientPrincipal.getName(), NT_USER_NAME),
                DEFAULT_LIFETIME,
                KERBEROS_OID,
                INITIATE_ONLY));

    return new GSSCredentialSession(clientCredential);
  }

  private static String defineServicePrincipal(String serviceName, String hostName, boolean useCanonicalHostname){
    String serviceHostName = useCanonicalHostname ? getCanonicalHostName(hostName) : hostName;
    return format(Locale.getDefault(), "%s@%s", serviceName, serviceHostName.toLowerCase(Locale.US));
  }

  private static String getCanonicalHostName(String hostName) {
    String canonicalHostName;
    try {
      InetAddress address = InetAddress.getByName(hostName);
      if ("localhost".equalsIgnoreCase(address.getHostName())) {
        canonicalHostName = InetAddress.getLocalHost().getCanonicalHostName();
      } else {
        canonicalHostName = address.getCanonicalHostName();
      }
    } catch (UnknownHostException e) {
      throw new RuntimeException("Failed to resolve host: " + hostName, e);
    }
    return canonicalHostName;
  }

  private interface GssSupplier<T> {
    T get() throws GSSException;
  }

  private static <T> T doAs(Subject subject, GssSupplier<T> action) throws GSSException {
    try {
      return Subject.doAs(subject, (PrivilegedExceptionAction<T>) action::get);
    } catch (PrivilegedActionException e) {
      Throwable t = e.getCause();
      if (t instanceof GSSException) {
        throw (GSSException)t;
      } else if (t instanceof Error) {
        throw (Error)t;
      } else if (t instanceof RuntimeException) {
        throw (RuntimeException)t;
      } else {
        throw new RuntimeException(t);
      }
    }
  }

  private static Oid createOid(String value) {
    try {
      return new Oid(value);
    } catch (GSSException e) {
      throw new AssertionError(e);
    }
  }

  private static class GSSCredentialSession {
    private final GSSCredential clientCredential;

    GSSCredentialSession(GSSCredential clientCredential) {
      requireNonNull(clientCredential, "gssCredential is null");
      this.clientCredential = clientCredential;
    }

    GSSCredential getClientCredential() {
      return clientCredential;
    }

    public boolean needsRefresh() throws GSSException {
      return clientCredential.getRemainingLifetime() < CREDENTIAL_EXPIRATION_THRESHOLD;
    }
  }

}