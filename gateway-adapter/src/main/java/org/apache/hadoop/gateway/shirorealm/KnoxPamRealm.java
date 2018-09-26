/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.hadoop.gateway.shirorealm;

import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.subject.PrincipalCollection;

/**
 * An adapter class that delegate calls to {@link org.apache.knox.gateway.shirorealm.KnoxPamRealm}
 * for backwards compatibility with package structure.
 *
 * This is class is deprecated and only used for backwards compatibility
 * please use
 * org.apache.knox.gateway.shirorealm.KnoxPamRealm
 * @since 1.0.0
 */
@Deprecated
public class KnoxPamRealm
    extends org.apache.knox.gateway.shirorealm.KnoxPamRealm {

  /**
   * Create an instance
   */
  public KnoxPamRealm() {
    super();
  }


  @Override
  protected AuthenticationInfo doGetAuthenticationInfo(
      AuthenticationToken token) throws AuthenticationException {
    return super.doGetAuthenticationInfo(token);
  }

  @Override
  protected AuthorizationInfo doGetAuthorizationInfo(
      PrincipalCollection principals) {
    return super.doGetAuthorizationInfo(principals);
  }
}
