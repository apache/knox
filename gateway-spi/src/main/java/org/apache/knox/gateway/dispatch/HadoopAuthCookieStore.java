/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.dispatch;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;
import java.util.Properties;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.knox.gateway.SpiGatewayMessages;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;

public class HadoopAuthCookieStore extends BasicCookieStore {
  private static final SpiGatewayMessages LOG = MessagesFactory.get(SpiGatewayMessages.class);

  private static final String HADOOP_AUTH_COOKIE_NAME = "hadoop.auth";
  private static final String HIVE_SERVER2_AUTH_COOKIE_NAME = "hive.server2.auth";
  private static final String IMPALA_AUTH_COOKIE_NAME = "impala.auth";

  private static String knoxPrincipal;

  HadoopAuthCookieStore(GatewayConfig config) {
    // Read knoxPrincipal from krb5 login jaas config file
    String krb5Config = config.getKerberosLoginConfig();
    if (krb5Config != null && !krb5Config.isEmpty()) {
      Properties p = new Properties();
      try (InputStream in = Files.newInputStream(Paths.get(krb5Config))){
        p.load(in);
        String configuredKnoxPrincipal = p.getProperty("principal");
        // Strip off enclosing quotes, if present
        if (configuredKnoxPrincipal.startsWith("\"")) {
          configuredKnoxPrincipal = configuredKnoxPrincipal.substring(1,
              configuredKnoxPrincipal.length() - 1);
        }
        knoxPrincipal = configuredKnoxPrincipal;
      } catch (IOException e) {
        LOG.errorReadingKerberosLoginConfig(krb5Config, e);
      }
    }
  }

  @Override
  public void addCookie(Cookie cookie) {
    // Only add the cookie if it is an auth cookie and belongs to Knox
    if (isAuthCookie(cookie) && isKnoxCookie(cookie)) {
      Wrapper wrapper = new Wrapper(cookie);
      LOG.acceptingServiceCookie(wrapper);
      super.addCookie(wrapper);
    }
  }

  private boolean isAuthCookie(Cookie cookie) {
    return HADOOP_AUTH_COOKIE_NAME.equals(cookie.getName()) ||
        HIVE_SERVER2_AUTH_COOKIE_NAME.equals(cookie.getName()) ||
        IMPALA_AUTH_COOKIE_NAME.equals(cookie.getName());
  }

  private boolean isKnoxCookie(Cookie cookie) {
    boolean result = false;

    // We expect cookies to be some delimited list of parameters, eg. username, principal,
    // timestamp, random number, etc. along with an HMAC signature. To ensure we only
    // store cookies that are relevant to Knox, we check that the Knox principal appears
    // somewhere in the cookie value.
    if (cookie != null) {
      String value = cookie.getValue();
      if (value != null && value.contains(knoxPrincipal)) {
        result = true;
      }
    }

    return result;
  }

  private static class Wrapper extends BasicClientCookie {
    private static final String DELEGATE_STR = "delegate";
    private final Cookie delegate;

    Wrapper(Cookie delegate ) {
      super(delegate.getName(), delegate.getValue());
      this.delegate = delegate;
    }

    @Override
    public String getName() {
      return delegate.getName();
    }

    /**
     * Checks the cookie value returned by the delegate and wraps it in double quotes if the value isn't
     * null, empty or already wrapped in double quotes.
     *
     * This change is required to workaround Oozie 4.3/Hadoop 2.4 not properly formatting the hadoop.auth cookie.
     * See the following jiras for additional context:
     *   https://issues.apache.org/jira/browse/HADOOP-10710
     *   https://issues.apache.org/jira/browse/HADOOP-10379
     * This issue was further compounded by another Oozie issue resulting in Kerberos replay attacks.
     *   https://issues.apache.org/jira/browse/OOZIE-2427
     */
    @Override
    public String getValue() {
      String value = delegate.getValue();
      if ( value != null && !value.isEmpty() ) {
        if( !value.startsWith( "\"" ) ) {
          value = "\"" + value;
        }
        if( !value.endsWith( "\"" ) ) {
          value = value + "\"";
        }
      }
      return value;
    }

    @Override
    public String getComment() {
      return delegate.getComment();
    }

    @Override
    public String getCommentURL() {
      return delegate.getCommentURL();
    }

    @Override
    public Date getExpiryDate() {
      return delegate.getExpiryDate();
    }

    @Override
    public boolean isPersistent() {
      return delegate.isPersistent();
    }

    @Override
    public String getDomain() {
      return delegate.getDomain();
    }

    @Override
    public String getPath() {
      return delegate.getPath();
    }

    @Override
    public int[] getPorts() {
      return delegate.getPorts();
    }

    @Override
    public boolean isSecure() {
      return delegate.isSecure();
    }

    @Override
    public int getVersion() {
      return delegate.getVersion();
    }

    @Override
    public boolean isExpired( Date date ) {
      return delegate.isExpired( date );
    }

    @Override
    public String toString() {
      return (new ReflectionToStringBuilder(this) {
        @Override
        protected boolean accept(Field f) {
          return super.accept(f) && !DELEGATE_STR.equals(f.getName());
        }
      }).toString();
    }
  }
}
