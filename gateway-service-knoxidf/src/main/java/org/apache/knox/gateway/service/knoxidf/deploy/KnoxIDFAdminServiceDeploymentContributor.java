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
package org.apache.knox.gateway.service.knoxidf.deploy;

import org.apache.knox.gateway.jersey.JerseyServiceDeploymentContributorBase;

/**
 * Deployment contributor for the KNOXIDF_ADMIN service role, which hosts all
 * KnoxIDF admin REST APIs under a single {@code knoxidf/admin/**?**} URL pattern.
 * Current resources: {@link org.apache.knox.gateway.service.knoxidf.TrustedOidcIssuersResource}.
 *
 * <p>The {@code knoxidf/admin/**?**} pattern is disjoint from the KNOXIDF role's
 * {@code knoxidf/api/**?**} pattern, preventing KNOXIDF from serving admin endpoints.</p>
 *
 * <p>Authorization: use {@code PathAclsAuthz} in the topology to assign independent
 * ACLs to each admin endpoint (e.g., {@code KNOXIDF_ADMIN.rule_issuers.path.acl}
 * for trusted-issuers). Alternatively, {@code AclsAuthz} with {@code KNOXIDF_ADMIN.acl}
 * applies a single ACL to all endpoints under this role.</p>
 */
public class KnoxIDFAdminServiceDeploymentContributor extends JerseyServiceDeploymentContributorBase {

  @Override
  public String getRole() {
    return "KNOXIDF_ADMIN";
  }

  @Override
  public String getName() {
    return "KNOXIDF_ADMIN";
  }

  @Override
  protected String[] getPackages() {
    return new String[] { "org.apache.knox.gateway.service.knoxidf" };
  }

  @Override
  protected String[] getPatterns() {
    return new String[] { "knoxidf/admin/**?**" };
  }
}
