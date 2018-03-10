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
package org.apache.knox.gateway.service.knoxs3;

import java.security.AccessController;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.security.auth.Subject;

import org.apache.knox.gateway.security.GroupPrincipal;
import org.apache.knox.gateway.security.ImpersonatedPrincipal;
import org.apache.knox.gateway.security.PrimaryPrincipal;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.amazonaws.services.securitytoken.model.Credentials;
import com.amazonaws.services.securitytoken.model.GetFederationTokenRequest;
import com.amazonaws.services.securitytoken.model.GetFederationTokenResult;

public class KnoxS3ClientBuilder {
  private Map<String, PolicyConfig> userPolicyConfig =  new HashMap<String, PolicyConfig>();
  private Map<String, PolicyConfig> groupPolicyConfig =  new HashMap<String, PolicyConfig>();

  public KnoxS3ClientBuilder() {
  }
  
  public AmazonS3 getS3Client() {
    BasicSessionCredentials sessionCredentials = getSessionCredentials();
    if (sessionCredentials == null) {
      throw new RuntimeException("No S3 credentials available.");
    }

    AmazonS3 s3 = AmazonS3ClientBuilder.standard().withRegion(Regions.US_EAST_1)
        .withCredentials(new AWSStaticCredentialsProvider(sessionCredentials)).build();
    return s3;
  }

  public void init(Properties context) {
    buildPolicyMaps(context);
  }

  private void buildPolicyMaps(Properties context) {
    /*
    <service>
    <role>KNOXS3</role>
    <param>
        <name>s3.user.policy.action.guest</name>
        <value>s3:Get*,s3:List*</value>
    </param>
    <param>
        <name>s3.user.policy.resource.guest</name>
        <value>*</value>
    </param>
    <param>
        <name>s3.group.policy.action.admin</name>
        <value>*</value>
    </param>
    <param>
        <name>s3.group.policy.resource.admin</name>
        <value>*</value>
    </param>
  </service>
  */

    String paramName = null;
    Enumeration<Object> e = context.keys();
    while (e.hasMoreElements()) {
      paramName = (String)e.nextElement();
      if (paramName.startsWith("s3.")) {
        String[] elements = paramName.split("\\.");
        if (elements[1].equals("user")) {
          PolicyConfig policy = userPolicyConfig.get(elements[4]);
          if (policy == null) {
            policy = new PolicyConfig();
            userPolicyConfig.put(elements[4], policy);
          }
          if (elements[3].equals("action")) {
            policy.actions=context.getProperty(paramName);
          } else {
            policy.resources=context.getProperty(paramName);
          }
          if (policy.actions != null && policy.resources != null) {
            buildS3PolicyModel(policy);
          }
        }else if (elements[1].equals("group")) {
          PolicyConfig policy = groupPolicyConfig.get(elements[4]);
          if (policy == null) {
            policy = new PolicyConfig();
            groupPolicyConfig.put(elements[4], policy);
          }
          if (elements[3].equals("action")) {
            policy.actions=context.getProperty(paramName);
          } else {
            policy.resources=context.getProperty(paramName);
          }
          if (policy.actions != null && policy.resources != null) {
            buildS3PolicyModel(policy);
          }
        }
      }
    }
  }

  private void buildS3PolicyModel(PolicyConfig policy) {
    S3PolicyModel model = new S3PolicyModel();
    model.setEffect("Allow");
    String[] actions = policy.actions.split(",");
    for (int i = 0; i < actions.length; i++) {
      model.addAction(actions[i]);
    }
    String[] resources = policy.resources.split(",");
    if (resources.length > 1) {
      for (int i = 0; i < resources.length; i++) {
        model.addResource(resources[i]);
      }
    } else {
      model.setResource(resources[0]);
    }
    policy.policy = model.toString();
  }

  private BasicSessionCredentials getSessionCredentials() {
    AWSSecurityTokenService sts_client = AWSSecurityTokenServiceClientBuilder.standard().withRegion(Regions.US_EAST_1).build();
    String policy = "";
    BasicSessionCredentials sessionCredentials = null;
    try {
      String username = null;
      Subject subject = Subject.getSubject(AccessController.getContext());
      username = getEffectiveUserName(subject);
      policy = buildPolicy(username, subject);

      if (policy != null) {
        GetFederationTokenRequest request = new GetFederationTokenRequest(username).withPolicy(policy);
        GetFederationTokenResult result = sts_client.getFederationToken(request);
        System.out.println(result.getCredentials());
  
        Credentials session_creds = result.getCredentials();
        sessionCredentials = new BasicSessionCredentials(
            session_creds.getAccessKeyId(),
            session_creds.getSecretAccessKey(),
            session_creds.getSessionToken());
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return sessionCredentials;
  }

  private String getEffectiveUserName(Subject subject) {
    String username;
    Principal primaryPrincipal = (Principal)subject.getPrincipals(PrimaryPrincipal.class).toArray()[0];
    Object[] impersonations = subject.getPrincipals(ImpersonatedPrincipal.class).toArray();
    if (impersonations.length > 0) {
      username = ((Principal)impersonations[0]).getName();
    }
    else {
      username = primaryPrincipal.getName();
    }
    return username;
  }

  private String buildPolicy(String username, Subject subject) {
    String policy = null;
    List<String> groupNames = new ArrayList<String>();
    Object[] groups = subject.getPrincipals(GroupPrincipal.class).toArray();
    for (int i = 0; i < groups.length; i++) {
      groupNames.add(
          ((Principal)groups[0]).getName());
    }
    
    PolicyConfig config = userPolicyConfig.get(username);
    if (config == null) {
      // check for a group policy match
      for (String groupName : groupNames) {
        config = groupPolicyConfig.get(groupName);
        if (config != null) {
          // just accept first match for now
          break;
        }
      }
    }
    if (config != null) {
      policy = config.policy;
    }
    return policy;
  }
  
  private class PolicyConfig {
    public String actions = null;
    public String resources = null;
    public String policy = null;
  }
}