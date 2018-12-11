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
package org.apache.knox.gateway.util;

import java.io.Serializable;
import java.security.Principal;


import org.apache.http.annotation.ThreadingBehavior;
import org.apache.http.annotation.Contract;
import org.apache.http.auth.Credentials;

/**
 * Simple {@link Credentials} implementation based on a user name / password
 * pair.
 *
 * @since 4.0
 */
@Contract(threading = ThreadingBehavior.IMMUTABLE_CONDITIONAL)
public class PrincipalCredentials implements Credentials, Serializable {

  private final Principal principal;

  public PrincipalCredentials( Principal principal ) {
    super();
    if( principal == null ) {
      throw new IllegalArgumentException( "principal==null" );
    }
    this.principal = principal;
  }

  @Override
  public Principal getUserPrincipal() {
    return principal;
  }

  @Override
  public String getPassword() {
    return null;
  }

  @Override
  public int hashCode() {
    return principal.hashCode();
  }

  @Override
  public boolean equals( Object object ) {
    if( this == object ) {
      return true;
    }
    if( object instanceof PrincipalCredentials ) {
      PrincipalCredentials that = (PrincipalCredentials)object;
      return this.principal.equals(that.principal);
    }
    return false;
  }

  @Override
  public String toString() {
    return this.principal.toString();
  }
}
