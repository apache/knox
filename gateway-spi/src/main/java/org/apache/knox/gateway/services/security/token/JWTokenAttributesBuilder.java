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

import java.security.Principal;
import java.util.Collections;
import java.util.List;

import javax.security.auth.Subject;

public class JWTokenAttributesBuilder {

  private Principal principal;
  private List<String> audiences;
  private String algorithm;
  private long expires;
  private String signingKeystoreName;
  private String signingKeystoreAlias;
  private char[] signingKeystorePassphrase;
  private boolean managed;
  private String jku;
  private String type;

  public JWTokenAttributesBuilder setPrincipal(Subject subject) {
    return setPrincipal((Principal) subject.getPrincipals().toArray()[0]);
  }

  public JWTokenAttributesBuilder setPrincipal(Principal principal) {
    this.principal = principal;
    return this;
  }

  public JWTokenAttributesBuilder setAudiences(String audience) {
    return setAudiences(Collections.singletonList(audience));
  }

  public JWTokenAttributesBuilder setAudiences(List<String> audiences) {
    this.audiences = audiences;
    return this;
  }

  public JWTokenAttributesBuilder setAlgorithm(String algorithm) {
    this.algorithm = algorithm;
    return this;
  }

  public JWTokenAttributesBuilder setExpires(long expires) {
    this.expires = expires;
    return this;
  }

  public JWTokenAttributesBuilder setSigningKeystoreName(String signingKeystoreName) {
    this.signingKeystoreName = signingKeystoreName;
    return this;
  }

  public JWTokenAttributesBuilder setSigningKeystoreAlias(String signingKeystoreAlias) {
    this.signingKeystoreAlias = signingKeystoreAlias;
    return this;
  }

  public JWTokenAttributesBuilder setSigningKeystorePassphrase(char[] signingKeystorePassphrase) {
    this.signingKeystorePassphrase = signingKeystorePassphrase;
    return this;
  }

  public JWTokenAttributesBuilder setManaged(boolean managed) {
    this.managed = managed;
    return this;
  }

  public JWTokenAttributesBuilder setJku(String jku) {
    this.jku = jku;
    return this;
  }

  public JWTokenAttributesBuilder setType(String type) {
    this.type = type;
    return this;
  }

  public JWTokenAttributes build() {
    return new JWTokenAttributes(principal, (audiences == null ? Collections.emptyList() : audiences), algorithm, expires, signingKeystoreName, signingKeystoreAlias,
        signingKeystorePassphrase, managed, jku, type);
  }

}
