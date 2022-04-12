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
package org.apache.knox.gateway.services.security.token;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.List;
import java.util.Set;

public class JWTokenAttributes {
  private final String userName;
  private final List<String> audiences;
  private final String algorithm;
  private final long expires;
  private final String signingKeystoreName;
  private final String signingKeystoreAlias;
  private final char[] signingKeystorePassphrase;
  private final boolean managed;
  private String jku;
  private final String type;
  private final Set<String> groups;
  private final String issuer;
  private String kid;

  JWTokenAttributes(String userName, List<String> audiences, String algorithm, long expires, String signingKeystoreName, String signingKeystoreAlias,
      char[] signingKeystorePassphrase, boolean managed, String jku, String type, Set<String> groups, String kid, String issuer) {
    this.userName = userName;
    this.audiences = audiences;
    this.algorithm = algorithm;
    this.expires = expires;
    this.signingKeystoreName = signingKeystoreName;
    this.signingKeystoreAlias = signingKeystoreAlias;
    this.signingKeystorePassphrase = signingKeystorePassphrase;
    this.managed = managed;
    this.jku = jku;
    this.type = type;
    this.groups = groups;
    this.kid = kid;
    this.issuer = issuer;
  }

  public String getUserName() {
    return userName;
  }

  public List<String> getAudiences() {
    return audiences;
  }

  public String getAlgorithm() {
    return algorithm;
  }

  public long getExpires() {
    return expires;
  }

  public Date getExpiresDate() {
    return expires == -1 ? null : new Date(expires);
  }

  public String getSigningKeystoreName() {
    return signingKeystoreName;
  }

  public String getSigningKeystoreAlias() {
    return signingKeystoreAlias;
  }

  public char[] getSigningKeystorePassphrase() {
    return signingKeystorePassphrase;
  }

  public boolean isManaged() {
    return managed;
  }

  public URI getJkuUri() throws URISyntaxException {
    return jku != null ? new URI(jku) : null;
  }

  public String getJku(){
    return jku;
  }

  public void setJku(String jku) {
    this.jku = jku;
  }

  public String getType() {
    return type;
  }

  public Set<String> getGroups() {
    return groups;
  }

  public void setKid(String kid) {
    this.kid = kid;
  }

  public String getKid() {
    return kid;
  }

  public String getIssuer() {
    return issuer;
  }
}
