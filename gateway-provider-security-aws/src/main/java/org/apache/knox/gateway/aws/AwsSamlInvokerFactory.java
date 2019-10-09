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
package org.apache.knox.gateway.aws;

import javax.servlet.FilterConfig;

/**
 * Factory to get implementation for {@link AwsSamlInvoker}
 */
public class AwsSamlInvokerFactory {

  public static final String AWS_SAML_FEDERATION_PROVIDER = "saml.aws.federation.provider";
  public static final String AWS_STS = "sts";
  public static final String AWS_LAKE_FORMATION = "lakeformation";

  public static AwsSamlInvoker getAwsSamlInvoker(FilterConfig filterConfig) {
    String provider = filterConfig.getInitParameter(AWS_SAML_FEDERATION_PROVIDER);
    if (provider != null) {
        return new AwsSimpleTokenServiceSamlImpl();
    }
    return new AwsSimpleTokenServiceSamlImpl();
  }
}
