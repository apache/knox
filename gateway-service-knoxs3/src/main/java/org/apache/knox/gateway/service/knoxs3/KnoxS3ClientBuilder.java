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
import org.apache.knox.gateway.security.SubjectUtils;
import org.apache.knox.gateway.service.idbroker.KnoxCloudCredentialsClient;
import org.apache.knox.gateway.service.idbroker.KnoxCloudCredentialsClientManager;
import org.apache.knox.gateway.service.idbroker.KnoxCloudPolicyProvider;
import org.apache.knox.gateway.service.idbroker.KnoxPolicyProviderManager;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
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
  private KnoxCloudPolicyProvider policyProvider = new KnoxPolicyProviderManager();
  private KnoxCloudCredentialsClient credentialsClient = new KnoxCloudCredentialsClientManager();

  private Properties props = null;

  public KnoxS3ClientBuilder() {
  }
  
  public AmazonS3 getS3Client() {
    GetFederationTokenResult result = (GetFederationTokenResult) credentialsClient.getCredentials();

    Credentials session_creds = result.getCredentials();
    BasicSessionCredentials sessionCredentials = new BasicSessionCredentials(
       session_creds.getAccessKeyId(),
       session_creds.getSecretAccessKey(),
       session_creds.getSessionToken());
    
    AmazonS3 s3 = AmazonS3ClientBuilder.standard().withRegion(Regions.US_EAST_1)
        .withCredentials(new AWSStaticCredentialsProvider(sessionCredentials)).build();
    return s3;
  }

  public void init(Properties context) {
    policyProvider.init(context);
    credentialsClient.init(context);
    credentialsClient.setPolicyProvider(policyProvider);
  }

  private String getEffectiveUserName(Subject subject) {
    return SubjectUtils.getEffectivePrincipalName(subject);
  }
}