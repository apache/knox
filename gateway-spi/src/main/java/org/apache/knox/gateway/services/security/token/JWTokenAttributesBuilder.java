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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class JWTokenAttributesBuilder {

  private String userName;
  private List<String> audiences;
  private String algorithm;
  private long issueTime;
  private long expires;
  private String signingKeystoreName;
  private String signingKeystoreAlias;
  private char[] signingKeystorePassphrase;
  private boolean managed;
  private String jku;
  private String type;
  private Set<String> groups;
  private String kid;
  private String issuer = JWTokenAttributes.DEFAULT_ISSUER;
  private String clientId;
  private List<Map<String, Object>> actorChain;
  private Map<String, Object> customAttributes;

  public JWTokenAttributesBuilder setUserName(String userName) {
    this.userName = userName;
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

  public JWTokenAttributesBuilder setIssueTime(long issueTime) {
    this.issueTime = issueTime;
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

  public JWTokenAttributesBuilder setGroups(Set<String> groups) {
    this.groups = groups;
    return this;
  }

  public JWTokenAttributesBuilder setKid(String kid) {
    this.kid = kid;
    return this;
  }

  public JWTokenAttributesBuilder setIssuer(String issuer) {
    this.issuer = issuer;
    return this;
  }

  public JWTokenAttributesBuilder setClientId(String clientId) {
    this.clientId = clientId;
    return this;
  }

  /**
   * Set the actor chain for RFC 8693 token exchange.
   *
   * <p>The actor chain represents the complete delegation history. Each element in the list
   * is a Map of identity claims that identify an actor. The list should be ordered from most
   * recent actor (first element) to oldest actor (last element).</p>
   *
   * <p>According to RFC 8693 Section 4.1, each actor should be identified using identity claims
   * such as 'sub' (subject) and 'iss' (issuer). Non-identity claims (e.g., 'exp', 'nbf', 'aud')
   * should NOT be included.</p>
   *
   * <p>Example:</p>
   * <pre>
   * List&lt;Map&lt;String, Object&gt;&gt; chain = new ArrayList&lt;&gt;();
   * Map&lt;String, Object&gt; actor1 = new HashMap&lt;&gt;();
   * actor1.put("sub", "service-a");
   * actor1.put("iss", "https://issuer.example.com");
   * chain.add(actor1);
   * builder.setActorChain(chain);
   * </pre>
   *
   * @param actorChain the actor chain, ordered from most recent to oldest
   * @return this builder
   */
  public JWTokenAttributesBuilder setActorChain(List<Map<String, Object>> actorChain) {
    this.actorChain = actorChain;
    return this;
  }

  public JWTokenAttributesBuilder setCustomAttributes(Map<String, Object> customAttributes) {
    this.customAttributes = customAttributes;
    return this;
  }

  public JWTokenAttributes build() {
    return new JWTokenAttributes(userName, (audiences == null ? new ArrayList<>() : audiences), algorithm, issueTime, expires, signingKeystoreName, signingKeystoreAlias,
        signingKeystorePassphrase, managed, jku, type, groups, kid, issuer, clientId, actorChain, customAttributes);
  }
}
