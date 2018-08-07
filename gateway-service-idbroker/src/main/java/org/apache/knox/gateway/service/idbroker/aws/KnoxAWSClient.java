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

import java.security.AccessController;
import java.util.Properties;

import javax.security.auth.Subject;

import org.apache.knox.gateway.security.SubjectUtils;
import org.apache.knox.gateway.service.idbroker.AbstractKnoxCloudCredentialsClient;
import org.apache.knox.gateway.service.idbroker.KnoxCloudCredentialsClient;
import org.apache.knox.gateway.services.security.AliasServiceException;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.amazonaws.services.securitytoken.model.GetFederationTokenRequest;
import com.amazonaws.services.securitytoken.model.GetFederationTokenResult;

public class KnoxAWSClient extends AbstractKnoxCloudCredentialsClient implements KnoxCloudCredentialsClient {

/* (non-Javadoc)
   * @see org.apache.knox.gateway.service.idbroker.KnoxCloudCredentialsClient#getCredentials()
   */
  @Override
  public Object getCredentials() {
    GetFederationTokenResult token = getFederationTokenResult();
    if (token == null) {
      // TODO: handle this more appropriately for an API!!!
      throw new RuntimeException("No AWS credentials available.");
    }
    return token;
  }

  private GetFederationTokenResult getFederationTokenResult() {
    String policy;
    AWSSecurityTokenService sts_client = AWSSecurityTokenServiceClientBuilder.standard().
    		withCredentials(new AliasServiceAWSCredentialsProvider()).
    		withRegion(Regions.US_EAST_1).build();
    String username = null;
    Subject subject = Subject.getSubject(AccessController.getContext());
    username = getEffectiveUserName(subject);
    policy = getPolicyProvider().getPolicy(username, subject);
    GetFederationTokenResult result = null;
    if (policy != null) {
      GetFederationTokenRequest request = new GetFederationTokenRequest(username).withPolicy(policy);
      result = sts_client.getFederationToken(request);
      System.out.println(result.getCredentials());
    }
    return result;
  }

  private String getEffectiveUserName(Subject subject) {
    return SubjectUtils.getEffectivePrincipalName(subject);
  }

  @Override
  public String getName() {
    return "AWS";
  }

  @Override
  public void init(Properties context) {
    super.init(context);
  }

  private class AliasServiceAWSCredentialsProvider
      implements AWSCredentialsProvider {
	@Override
	public AWSCredentials getCredentials() {
	  return new AWSCredentials() {
        @Override
        public String getAWSAccessKeyId() {
          try {
			return new String(aliasService.
			    getPasswordFromAliasForCluster(topologyName, "aws.credentials.key"));
		  } catch (AliasServiceException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		  }
          return null;
		}
		@Override
		public String getAWSSecretKey() {
          try {
			return new String(aliasService.
			    getPasswordFromAliasForCluster(topologyName, "aws.credentials.secret"));
		  } catch (AliasServiceException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		  }
          return null;
		}
	  };
	}

	@Override
	public void refresh() {		
	}
	  
  }

}
