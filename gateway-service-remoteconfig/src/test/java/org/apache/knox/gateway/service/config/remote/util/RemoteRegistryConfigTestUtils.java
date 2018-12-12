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
package org.apache.knox.gateway.service.config.remote.util;

import java.util.Collection;
import java.util.Map;

public class RemoteRegistryConfigTestUtils {

    public static final String PROPERTY_TYPE = "type";
    public static final String PROPERTY_NAME = "name";
    public static final String PROPERTY_ADDRESS = "address";
    public static final String PROPERTY_NAMESAPCE = "namespace";
    public static final String PROPERTY_SECURE = "secure";
    public static final String PROPERTY_AUTH_TYPE = "authType";
    public static final String PROPERTY_PRINCIPAL = "principal";
    public static final String PROPERTY_CRED_ALIAS = "credentialAlias";
    public static final String PROPERTY_KEYTAB = "keyTab";
    public static final String PROPERTY_USE_KEYTAB = "useKeyTab";
    public static final String PROPERTY_USE_TICKET_CACHE = "useTicketCache";

    public static String createRemoteConfigRegistriesXML(Collection<Map<String, String>> configProperties) {
        StringBuilder result = new StringBuilder(128);
        result.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<remote-configuration-registries>\n");

        for (Map<String, String> props : configProperties) {
            String authType = props.get(PROPERTY_AUTH_TYPE);
            if ("Kerberos".equalsIgnoreCase(authType)) {
                result.append(createRemoteConfigRegistryXMLWithKerberosAuth(props.get(PROPERTY_TYPE),
                    props.get(PROPERTY_NAME),
                    props.get(PROPERTY_ADDRESS),
                    props.get(PROPERTY_PRINCIPAL),
                    props.get(PROPERTY_KEYTAB),
                    Boolean.valueOf(props.get(PROPERTY_USE_KEYTAB)),
                    Boolean.valueOf(props.get(PROPERTY_USE_TICKET_CACHE))));
            } else if ("Digest".equalsIgnoreCase(authType)) {
                result.append(createRemoteConfigRegistryXMLWithDigestAuth(props.get(PROPERTY_TYPE),
                    props.get(PROPERTY_NAME),
                    props.get(PROPERTY_ADDRESS),
                    props.get(PROPERTY_PRINCIPAL),
                    props.get(PROPERTY_CRED_ALIAS)));
            } else {
                result.append(createRemoteConfigRegistryXMLNoAuth(props.get(PROPERTY_TYPE),
                    props.get(PROPERTY_NAME),
                    props.get(PROPERTY_ADDRESS)));
            }
        }

        result.append("</remote-configuration-registries>\n");

        return result.toString();
    }

    public static String createRemoteConfigRegistryXMLWithKerberosAuth(String type,
                                                                       String name,
                                                                       String address,
                                                                       String principal,
                                                                       String keyTab,
                                                                       boolean userKeyTab,
                                                                       boolean useTicketCache) {
        return "  <remote-configuration-registry>\n" +
               "    <name>" + name + "</name>\n" +
               "    <type>" + type + "</type>\n" +
               "    <address>" + address + "</address>\n" +
               "    <secure>true</secure>\n" +
               "    <auth-type>" + "Kerberos" + "</auth-type>\n" +
               "    <principal>" + principal + "</principal>\n" +
               "    <keytab>" + keyTab + "</keytab>\n" +
               "    <use-keytab>" + userKeyTab + "</use-keytab>\n" +
               "    <use-ticket-cache>" + useTicketCache + "</use-ticket-cache>\n" +
               "  </remote-configuration-registry>\n";
    }

    public static String createRemoteConfigRegistryXMLWithDigestAuth(String type,
                                                                     String name,
                                                                     String address,
                                                                     String principal,
                                                                     String credentialAlias) {
        return "  <remote-configuration-registry>\n" +
               "    <name>" + name + "</name>\n" +
               "    <type>" + type + "</type>\n" +
               "    <address>" + address + "</address>\n" +
               "    <secure>true</secure>\n" +
               "    <auth-type>" + "Digest" + "</auth-type>\n" +
               "    <principal>" + principal + "</principal>\n" +
               "    <credential-alias>" + credentialAlias + "</credential-alias>\n" +
               "  </remote-configuration-registry>\n";
    }


    public static String createRemoteConfigRegistryXMLNoAuth(String type,
                                                             String name,
                                                             String address) {
        return "  <remote-configuration-registry>\n" +
               "    <name>" + name + "</name>\n" +
               "    <type>" + type + "</type>\n" +
               "    <address>" + address + "</address>\n" +
               "  </remote-configuration-registry>\n";
    }

}
