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
package org.apache.knox.gateway.aws.model;

import java.util.regex.Pattern;

/**
 * Representation of AWS Role, Principal pairs in SAML attribute.
 * <p>
 * Valid AWS Role assertion contain a Role ARN and a Principal ARN. There is a comma between the
 * two.
 */
public class AwsRolePrincipalSamlPair {

  public static final String PAIR_SPLITTER = ",";
  public static final int PARTS_AFTER_SPLIT = 2;
  private static final Pattern ROLE_REGEX = Pattern
      .compile("arn:aws(-[\\w]+)*:iam::[0-9]{12}:role/.*");
  private static final Pattern PRINCIPAL_REGEX = Pattern
      .compile("arn:aws(-[\\w]+)*:iam::[0-9]{12}:saml-provider/.*");
  private String roleArn;
  private String principalArn;


  public AwsRolePrincipalSamlPair(String samlAttribute) {
    String[] splits = samlAttribute.split(PAIR_SPLITTER);
    if (splits.length == PARTS_AFTER_SPLIT && ROLE_REGEX.matcher(splits[0]).matches()
        && PRINCIPAL_REGEX.matcher(splits[1]).matches()) { // Ignore incorrect mappings
      roleArn = splits[0];
      principalArn = splits[1];
    } else {
      throw new IllegalArgumentException(
          "AWS SAML Role Attribute Value is incorrect " + samlAttribute);
    }
  }

  public AwsRolePrincipalSamlPair(String roleArn, String principalArn) {
    this.roleArn = roleArn;
    this.principalArn = principalArn;
  }

  public String getRoleArn() {
    return roleArn;
  }

  public String getPrincipalArn() {
    return principalArn;
  }

  public String getRoleName() {
    int slashIndex = roleArn.lastIndexOf('/');
    return roleArn.substring(slashIndex + 1);
  }

  public String getPrincipalName() {
    int slashIndex = principalArn.lastIndexOf('/');
    return principalArn.substring(slashIndex + 1);
  }
}
