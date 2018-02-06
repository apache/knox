/**
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

import java.lang.reflect.Field;
import java.util.Date;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.knox.gateway.SpiGatewayMessages;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;

public class HadoopAuthCookieStore extends BasicCookieStore {

  private static SpiGatewayMessages LOG = MessagesFactory.get( SpiGatewayMessages.class );

  @Override
  public void addCookie(Cookie cookie) {
    if (cookie.getName().equals("hadoop.auth") || cookie.getName().equals("hive.server2.auth")) {
      Wrapper wrapper = new Wrapper( cookie );
      LOG.acceptingServiceCookie( wrapper );
      super.addCookie( wrapper );
    }
  }

  private static class Wrapper extends BasicClientCookie {

    private Cookie delegate;

    Wrapper( Cookie delegate ) {
      super( delegate.getName(), delegate.getValue() );
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

    public String toString() {
      return (new ReflectionToStringBuilder(this) {
        protected boolean accept(Field f) {
          return super.accept(f) && !f.getName().equals("delegate");
        }
      }).toString();
    }

  }

}