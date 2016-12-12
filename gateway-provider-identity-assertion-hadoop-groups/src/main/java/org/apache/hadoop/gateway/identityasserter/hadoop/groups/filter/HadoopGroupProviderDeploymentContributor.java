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
package org.apache.hadoop.gateway.identityasserter.hadoop.groups.filter;

import org.apache.hadoop.gateway.identityasserter.common.filter.AbstractIdentityAsserterDeploymentContributor;

/**
 * A provider deployment contributor for looking up authenticated user groups as
 * seen by Hadoop implementation.
 * 
 * @since 0.11.0
 */

public class HadoopGroupProviderDeploymentContributor
    extends AbstractIdentityAsserterDeploymentContributor {

  /**
   * Name of our <b>identity-assertion</b> provider.
   */
  public static final String HADOOP_GROUP_PROVIDER = "HadoopGroupProvider";

  /* create an instance */
  public HadoopGroupProviderDeploymentContributor() {
    super();
  }

  /*
   * (non-Javadoc)
   * 
   * @see
   * org.apache.hadoop.gateway.deploy.ProviderDeploymentContributor#getName()
   */
  @Override
  public String getName() {
    return HADOOP_GROUP_PROVIDER;
  }

  /*
   * (non-Javadoc)
   * 
   * @see org.apache.hadoop.gateway.identityasserter.common.filter.
   * AbstractIdentityAsserterDeploymentContributor#getFilterClassname()
   */
  @Override
  protected String getFilterClassname() {
    return HadoopGroupProviderFilter.class.getName();
  }

}
