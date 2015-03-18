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
package org.apache.hadoop.gateway.picketlink.deploy;

/**
 * Provides a serializable configuration file for adding to
 * the webapp as an XML string for picketlink.xml
 *
 */
public class PicketlinkConf {
  public static final String INDENT = "    ";
  public static final String LT_OPEN = "<";
  public static final String LT_CLOSE = "</";
  public static final String GT = ">";
  public static final String GT_CLOSE = "/>";
  public static final String NL = "\n";
  public static final String PICKETLINK_XMLNS = "urn:picketlink:identity-federation:config:2.1";
  public static final String PICKETLINK_SP_XMLNS = "urn:picketlink:identity-federation:config:1.0";
  public static final String C14N_METHOD = "http://www.w3.org/2001/10/xml-exc-c14n#";
  public static final String KEYPROVIDER_ELEMENT = "KeyProvider";
  public static final String KEYPROVIDER_CLASSNAME = "org.picketlink.identity.federation.core.impl.KeyStoreKeyManager";
  public static final String AUTH_HANDLER_CLASSNAME = "org.picketlink.identity.federation.web.handlers.saml2.SAML2AuthenticationHandler";
  public static final String ROLE_GEN_HANDLER_CLASSNAME = "org.picketlink.identity.federation.web.handlers.saml2.RolesGenerationHandler";
  public static final String PICKETLINK_ELEMENT = "PicketLink";
  public static final String PICKETLINKSP_ELEMENT = "PicketLinkSP";
  public static final String HANDLERS_ELEMENT = "Handlers";
  public static final String HANDLER_ELEMENT = "Handler";
  public static final String OPTION_ELEMENT = "Option";
  public static final String VAL_ALIAS_ELEMENT = "ValidatingAlias";
  public static final String AUTH_ELEMENT = "Auth";

  private String serverEnvironment = "jetty";
  private String bindingType = "POST";
  private String idpUsesPostingBinding = "true";
  private String supportsSignatures = "true";
  private String identityURL = null;
  private String serviceURL = null;
  private String keystoreURL = null;
  private String keystorePass = null;
  private String signingKeyAlias = null;
  private String signingKeyPass = null;
  private String validatingKeyAlias = null;
  private String validatingKeyValue = null;
  private String nameIDFormat = "urn:oasis:names:tc:SAML:2.0:nameid-format:persistent";
  private String clockSkewMilis = null;
  private String assertionSessionAttributeName = "org.picketlink.sp.assertion";
  
  public String getServerEnvironment() {
    return serverEnvironment;
  }
  public void setServerEnvironment(String serverEnvironment) {
    this.serverEnvironment = serverEnvironment;
  }
  public String getBindingType() {
    return bindingType;
  }
  public void setBindingType(String bindingType) {
    this.bindingType = bindingType;
  }
  public String getIdpUsesPostingBinding() {
    return idpUsesPostingBinding;
  }
  public void setIdpUsesPostingBinding(String idpUsesPostingBinding) {
    this.idpUsesPostingBinding = idpUsesPostingBinding;
  }
  public String getSupportsSignatures() {
    return supportsSignatures;
  }
  public void setSupportsSignatures(String supportsSignatures) {
    this.supportsSignatures = supportsSignatures;
  }
  public String getIdentityURL() {
    return identityURL;
  }
  public void setIdentityURL(String identityURL) {
    this.identityURL = identityURL;
  }
  public String getServiceURL() {
    return serviceURL;
  }
  public void setServiceURL(String serviceURL) {
    this.serviceURL = serviceURL;
  }
  public String getKeystoreURL() {
    return keystoreURL;
  }
  public void setKeystoreURL(String keystoreURL) {
    this.keystoreURL = keystoreURL;
  }
  public String getKeystorePass() {
    return keystorePass;
  }
  public void setKeystorePass(String keystorePass) {
    this.keystorePass = keystorePass;
  }
  public String getSigningKeyAlias() {
    return signingKeyAlias;
  }
  public void setSigningKeyAlias(String signingKeyAlias) {
    this.signingKeyAlias = signingKeyAlias;
  }
  public String getSigningKeyPass() {
    return signingKeyPass;
  }
  public void setSigningKeyPass(String signingKeyPass) {
    this.signingKeyPass = signingKeyPass;
  }
  public String getValidatingKeyAlias() {
    return validatingKeyAlias;
  }
  public void setValidatingAliasKey(String validatingKeyAlias) {
    this.validatingKeyAlias = validatingKeyAlias;
  }
  public String getValidatingKeyValue() {
    return validatingKeyValue;
  }
  public void setValidatingAliasValue(String validatingKeyValue) {
    this.validatingKeyValue = validatingKeyValue;
  }
  public String getNameIDFormat() {
    return nameIDFormat;
  }
  public void setNameIDFormat(String nameIDFormat) {
    this.nameIDFormat = nameIDFormat;
  }
  public String getClockSkewMilis() {
    return clockSkewMilis;
  }
  public void setClockSkewMilis(String clockSkewMilis) {
    this.clockSkewMilis = clockSkewMilis;
  }
  public String getAssertionSessionAttributeName() {
    return assertionSessionAttributeName;
  }
  public void setAssertionSessionAttributeName(
      String assertionSessionAttributeName) {
    this.assertionSessionAttributeName = assertionSessionAttributeName;
  }
  @Override
  public String toString() {
    // THIS IS HORRID REPLACE WITH DOM+TRANSFORM
    StringBuffer xml = new StringBuffer();
    xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>").append(NL)
    .append(LT_OPEN).append(PICKETLINK_ELEMENT).append(" xmlns=\"").append(PICKETLINK_XMLNS).append("\"" + GT).append(NL)
      .append(INDENT).append(LT_OPEN).append(PICKETLINKSP_ELEMENT).append(" xmlns=\"").append(PICKETLINK_SP_XMLNS + "\"").append(NL)
      .append(INDENT).append(INDENT).append("ServerEnvironment").append("=\"").append(serverEnvironment).append("\"").append(NL)
      .append(INDENT).append(INDENT).append("BindingType").append("=\"").append(bindingType).append("\"").append(NL)
      .append(INDENT).append(INDENT).append("IDPUsesPostBinding").append("=\"").append(idpUsesPostingBinding).append("\"").append(NL)
      .append(INDENT).append(INDENT).append("SupportsSignatures").append("=\"").append(supportsSignatures).append("\"").append(NL)
      .append(INDENT).append(INDENT).append("CanonicalizationMethod").append("=\"").append(C14N_METHOD).append("\"").append(GT).append(NL).append(NL)
      .append(INDENT).append(INDENT).append(LT_OPEN).append("IdentityURL").append(GT).append(identityURL).append(LT_CLOSE).append("IdentityURL").append(GT).append(NL)
      .append(INDENT).append(INDENT).append(LT_OPEN).append("ServiceURL").append(GT).append(serviceURL).append(LT_CLOSE).append("ServiceURL").append(GT).append(NL)
      .append(INDENT).append(INDENT).append(LT_OPEN).append(KEYPROVIDER_ELEMENT).append(" ").append("ClassName=\"").append(KEYPROVIDER_CLASSNAME + "\"" + GT).append(NL)
        .append(INDENT).append(INDENT).append(INDENT).append(LT_OPEN).append(AUTH_ELEMENT).append(" Key=\"KeyStoreURL\" Value=\"").append(keystoreURL).append("\"").append(GT_CLOSE).append(NL)
        .append(INDENT).append(INDENT).append(INDENT).append(LT_OPEN).append(AUTH_ELEMENT).append(" Key=\"KeyStorePass\" Value=\"").append(keystorePass).append("\"").append(GT_CLOSE).append(NL)
        .append(INDENT).append(INDENT).append(INDENT).append(LT_OPEN).append(AUTH_ELEMENT).append(" Key=\"SigningKeyAlias\" Value=\"").append(signingKeyAlias).append("\"").append(GT_CLOSE).append(NL)
        .append(INDENT).append(INDENT).append(INDENT).append(LT_OPEN).append(AUTH_ELEMENT).append(" Key=\"SigningKeyPass\" Value=\"").append(signingKeyPass).append("\"").append(GT_CLOSE).append(NL)
        .append(INDENT).append(INDENT).append(INDENT).append(LT_OPEN).append(VAL_ALIAS_ELEMENT).append(" Key=\"").append(validatingKeyAlias).append("\" Value=\"").append(validatingKeyValue).append("\"").append(GT_CLOSE).append(NL)
      .append(INDENT).append(INDENT).append(LT_CLOSE).append(KEYPROVIDER_ELEMENT).append(GT).append(NL)
      .append(INDENT).append(LT_CLOSE).append(PICKETLINKSP_ELEMENT).append(GT).append(NL)
      .append(INDENT).append(LT_OPEN).append(HANDLERS_ELEMENT).append(GT).append(NL)
        .append(INDENT).append(INDENT).append(LT_OPEN).append(HANDLER_ELEMENT).append(" class=\"").append(AUTH_HANDLER_CLASSNAME).append("\">").append(NL)
          .append(INDENT).append(INDENT).append(INDENT).append(LT_OPEN).append(OPTION_ELEMENT).append(" Key=\"NAMEID_FORMAT\" Value=\"").append(nameIDFormat).append("\"").append(GT_CLOSE).append(NL)
          .append(INDENT).append(INDENT).append(INDENT).append(LT_OPEN).append(OPTION_ELEMENT).append(" Key=\"CLOCK_SKEW_MILIS\" Value=\"").append(clockSkewMilis).append("\"").append(GT_CLOSE).append(NL)
          .append(INDENT).append(INDENT).append(INDENT).append(LT_OPEN).append(OPTION_ELEMENT).append(" Key=\"ASSERTION_SESSION_ATTRIBUTE_NAME\" Value=\"").append(assertionSessionAttributeName).append("\"").append(GT_CLOSE).append(NL)
        .append(INDENT).append(INDENT).append(LT_CLOSE).append(HANDLER_ELEMENT).append(GT).append(NL)
        .append(INDENT).append(INDENT).append(LT_OPEN).append(HANDLER_ELEMENT).append(" class=\"").append(ROLE_GEN_HANDLER_CLASSNAME).append("\"/>").append(NL)
      .append(INDENT).append(LT_CLOSE).append(HANDLERS_ELEMENT).append(GT).append(NL)
    .append(LT_CLOSE).append(PICKETLINK_ELEMENT).append(GT).append(NL);
     
    return xml.toString();
  }
  
  public static void main(String[] args) {
    PicketlinkConf conf = new PicketlinkConf();
    System.out.println(conf.toString());
  }

}
