/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.provider.federation;

import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.SignedJWT;
import org.apache.knox.gateway.provider.federation.jwt.filter.AbstractJWTFilter;
import org.apache.knox.gateway.provider.federation.jwt.filter.JWTFederationFilter;
import org.apache.knox.gateway.security.ActorChainPrincipal;
import org.apache.knox.gateway.security.CommonTokenConstants;
import org.apache.knox.gateway.security.PrimaryPrincipal;
import org.apache.knox.gateway.security.SubjectUtils;
import org.apache.knox.gateway.security.TokenExchangePrincipal;
import org.apache.knox.gateway.services.security.token.JWTokenAttributesBuilder;
import org.apache.knox.gateway.services.security.token.impl.JWTToken;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.security.Principal;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static org.apache.knox.gateway.provider.federation.jwt.filter.AbstractJWTFilter.JWT_DEFAULT_ISSUER;

/**
 * Unit tests for the {@link JWTFederationFilter#handleTokenExchange} method (OIDC
 * delegation path). Each test verifies the Subject constructed.
 *
 * <p>These tests use {@link TestJWTFederationFilter} with {@link TestJWTokenAuthority} (static key,
 * no mocking needed — both tokens use Knox issuer {@code JWT_DEFAULT_ISSUER} which is in the
 * static expected-issuers list).
 *
 * <p>The filter's {@code continueWithEstablishedSecurityContext} runs
 * {@code Subject.doAs(subject, () -> chain.doFilter(request, response))}. The
 * {@link AbstractJWTFilterTest.TestFilterChain} captures {@code SubjectUtils.getCurrentSubject()}
 * from within that doAs context, which is exactly the Subject built. All principal
 * assertions use {@code chain.subject.getPrincipals(XxxPrincipal.class)}.
 */
public class JWTFederationFilterHandleTokenExchangeTest extends AbstractJWTFilterTest {

  static final String ACTOR_ISSUER = "https://actor.oidc.example.com";

  @Before
  public void setUp() throws Exception {
    handler = new TestJWTFederationFilter();
    ((TestJWTFederationFilter) handler).setTokenService(new TestJWTokenAuthority(publicKey));
    handler.init(new TestFilterConfig(getProperties()));
  }

  @Override
  protected void setTokenOnRequest(HttpServletRequest request, SignedJWT jwt) {
    EasyMock.expect(request.getHeader("Authorization"))
        .andReturn(JWTFederationFilter.BEARER + jwt.serialize()).anyTimes();
  }

  @Override
  protected void setGarbledTokenOnRequest(HttpServletRequest request, SignedJWT jwt) {
    EasyMock.expect(request.getHeader("Authorization"))
        .andReturn(JWTFederationFilter.BEARER + "ljm" + jwt.serialize()).anyTimes();
  }

  @Override
  protected String getAudienceProperty() {
    return JWTFederationFilter.KNOX_TOKEN_AUDIENCES;
  }

  @Override
  protected String getVerificationPemProperty() {
    return JWTFederationFilter.TOKEN_VERIFICATION_PEM;
  }

  /**
   * When both subject_token and actor_token are present, the filter establishes the actor
   * (from actor_token.sub) as the PrimaryPrincipal in the resulting Subject.
   */
  @Test
  public void testActorAndSubjectTokensSetActorAsPrimaryPrincipal() throws Exception {
    SignedJWT subjectJwt = getJWT(JWT_DEFAULT_ISSUER, "k8s-sa",
        new Date(System.currentTimeMillis() + 60000), privateKey);
    SignedJWT actorJwt = getJWT(JWT_DEFAULT_ISSUER, "actor-svc",
        new Date(System.currentTimeMillis() + 60000), privateKey);

    HttpServletRequest request = buildTokenExchangeRequest(subjectJwt.serialize(), actorJwt.serialize());
    EasyMock.replay(request);
    HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    EasyMock.replay(response);

    TestFilterChain chain = new TestFilterChain();
    handler.doFilter(request, response, chain);

    Assert.assertTrue("doFilterCalled should be true", chain.doFilterCalled);
    Set<PrimaryPrincipal> principals = chain.subject.getPrincipals(PrimaryPrincipal.class);
    Assert.assertEquals("Expected exactly one PrimaryPrincipal", 1, principals.size());
    Assert.assertEquals("Expected actor as PrimaryPrincipal", "actor-svc",
        ((Principal) principals.toArray()[0]).getName());
  }

  /**
   * When subject_token and actor_token have different issuers, the filter creates a
   * TokenExchangePrincipal that carries the subject and actor identities with their respective
   * issuers. Using different issuers ensures that all four TEP fields can be asserted
   * unambiguously.
   *
   * <p>Both issuers are added to the static {@code jwt.expected.issuer} whitelist —
   * {@code TestJWTokenAuthority} accepts either token because they are signed with
   * the same test key.
   */
  @Test
  public void testActorAndSubjectTokensCreateTokenExchangePrincipal() throws Exception {
    Properties props = getProperties();
    props.setProperty(AbstractJWTFilter.JWT_EXPECTED_ISSUER, JWT_DEFAULT_ISSUER + "," + ACTOR_ISSUER);
    handler.init(new TestFilterConfig(props));

    SignedJWT subjectJwt = getJWT(JWT_DEFAULT_ISSUER, "k8s-sa",
        new Date(System.currentTimeMillis() + 60000), privateKey);
    SignedJWT actorJwt = getJWT(ACTOR_ISSUER, "actor-svc",
        new Date(System.currentTimeMillis() + 60000), privateKey);

    HttpServletRequest request = buildTokenExchangeRequest(subjectJwt.serialize(), actorJwt.serialize());
    EasyMock.replay(request);
    HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    EasyMock.replay(response);

    TestFilterChain chain = new TestFilterChain();
    handler.doFilter(request, response, chain);

    Assert.assertTrue("doFilterCalled should be true", chain.doFilterCalled);
    TokenExchangePrincipal tep = SubjectUtils.getTokenExchangePrincipal(chain.subject);
    Assert.assertNotNull("TokenExchangePrincipal should be present", tep);
    Assert.assertEquals("Subject principal name", "k8s-sa", tep.getSubjectPrincipalName());
    Assert.assertEquals("Actor principal name", "actor-svc", tep.getActorPrincipalName());
    Assert.assertEquals("Subject issuer", JWT_DEFAULT_ISSUER, tep.getSubjectIssuer());
    Assert.assertEquals("Actor issuer", ACTOR_ISSUER, tep.getActorIssuer());
  }

  /**
   * When subject_token carries an {@code act} claim (a prior delegation chain), the filter
   * extracts it and creates an {@code ActorChainPrincipal} in the resulting Subject so that
   * the delegation history is preserved through the filter pipeline.
   */
  @Test
  public void testSubjectTokenWithActClaimCreatesActorChainPrincipal() throws Exception {
    List<Map<String, Object>> actorChainData = List.of(Map.of("sub", "prior-actor"));
    JWTToken subjectToken = new JWTToken(new JWTokenAttributesBuilder()
        .setUserName("k8s-sa")
        .setIssuer(JWT_DEFAULT_ISSUER)
        .setAlgorithm("RS256")
        .setExpires(System.currentTimeMillis() + 60000)
        .setActorChain(actorChainData)
        .build());
    subjectToken.sign(new RSASSASigner(privateKey));

    SignedJWT actorJwt = getJWT(JWT_DEFAULT_ISSUER, "actor-svc",
        new Date(System.currentTimeMillis() + 60000), privateKey);

    HttpServletRequest request = buildTokenExchangeRequest(subjectToken.toString(), actorJwt.serialize());
    EasyMock.replay(request);
    HttpServletResponse response = EasyMock.createNiceMock(HttpServletResponse.class);
    EasyMock.replay(response);

    TestFilterChain chain = new TestFilterChain();
    handler.doFilter(request, response, chain);

    Assert.assertTrue("doFilterCalled should be true", chain.doFilterCalled);
    Set<ActorChainPrincipal> actorChainPrincipals = chain.subject.getPrincipals(ActorChainPrincipal.class);
    Assert.assertFalse("ActorChainPrincipal should be present", actorChainPrincipals.isEmpty());
    ActorChainPrincipal acp = actorChainPrincipals.iterator().next();
    Assert.assertEquals("Expected current actor from act claim", "prior-actor", acp.getCurrentActor());
  }

  /**
   * Builds a token-exchange request mock with both subject_token and actor_token parameters.
   * The caller must call {@code EasyMock.replay(request)} before using the returned mock.
   *
   * @param subjectToken serialized subject JWT
   * @param actorToken   serialized actor JWT
   * @return a NiceMock HttpServletRequest configured for a token-exchange grant
   */
  private HttpServletRequest buildTokenExchangeRequest(String subjectToken, String actorToken) {
    HttpServletRequest request = EasyMock.createNiceMock(HttpServletRequest.class);
    EasyMock.expect(request.getRequestURL())
        .andReturn(new StringBuffer(SERVICE_URL)).anyTimes();
    EasyMock.expect(request.getParameter(CommonTokenConstants.GRANT_TYPE))
        .andReturn(JWTFederationFilter.TOKEN_EXCHANGE).anyTimes();
    EasyMock.expect(request.getParameter(JWTFederationFilter.SUBJECT_TOKEN))
        .andReturn(subjectToken).anyTimes();
    EasyMock.expect(request.getParameter(JWTFederationFilter.ACTOR_TOKEN))
        .andReturn(actorToken).anyTimes();
    return request;
  }
}
