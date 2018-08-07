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
package org.apache.knox.gateway.service.idbroker.aws;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.security.auth.Subject;

import org.apache.knox.gateway.security.GroupPrincipal;
import org.apache.knox.gateway.service.idbroker.KnoxCloudPolicyProvider;

public class KnoxAWSPolicyProvider implements KnoxCloudPolicyProvider {
  private Map<String, PolicyConfig> userPolicyConfig =  new HashMap<String, PolicyConfig>();
  private Map<String, PolicyConfig> groupPolicyConfig =  new HashMap<String, PolicyConfig>();

  public KnoxAWSPolicyProvider() {
  }

  /* (non-Javadoc)
   * @see org.apache.knox.gateway.service.idbroker.KnoxCloudPolicyProvider#init(java.util.Properties)
   */
  @Override
  public void init(Properties context) {
    buildPolicyMaps(context);
  }

  private void buildPolicyMaps(Properties context) {
    /*
    <service>
    <role>IDBROKER</role>
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
        }
      }
    }
  }

  private AWSPolicyModel buildAWSPolicyModel(PolicyConfig policy) {
    AWSPolicyModel model = new AWSPolicyModel();
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
    return model;
  }

  /* (non-Javadoc)
   * @see org.apache.knox.gateway.service.idbroker.KnoxCloudPolicyProvider#buildPolicy(java.lang.String, javax.security.auth.Subject)
   */
  @Override
  public String getPolicy(String username, Subject subject) {
    String policy = null;
    List<String> groupNames = getGroupNames(subject);

    PolicyConfig userConfig = userPolicyConfig.get(username);
    // check for a group policy match
    PolicyConfig config = null;
    AWSPolicyModel model = null;
    if (userConfig != null) {
      model = buildAWSPolicyModel(userConfig); 
    }
    for (String groupName : groupNames) {
      config = groupPolicyConfig.get(groupName);
      if (config != null) {
        if (model != null) {
          model.combine(buildAWSPolicyModel(config));
        }
        else {
          model = buildAWSPolicyModel(config);
        }
      }
    }
    return model.toString();
  }

  private List<String> getGroupNames(Subject subject) {
    List<String> groupNames = new ArrayList<String>();
    Object[] groups = subject.getPrincipals(GroupPrincipal.class).toArray();
    for (int i = 0; i < groups.length; i++) {
      groupNames.add(
          ((Principal)groups[0]).getName());
    }
    return groupNames;
  }

  private class PolicyConfig {
    public String actions = null;
    public String resources = null;
  }

  @Override
  public String getName() {
    return "default";
  }
}