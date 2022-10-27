/*
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
package org.apache.knox.gateway.hive;

import org.apache.knox.gateway.config.Configure;
import org.apache.knox.gateway.config.Default;
import org.apache.knox.gateway.dispatch.ConfigurableDispatch;
import org.apache.http.client.methods.HttpUriRequest;

/**
 * This specialized dispatch provides Hive specific features to the
 * default HttpClientDispatch.
 */
public class HiveDispatch extends ConfigurableDispatch {
  private boolean basicAuthPreemptive;

  @Override
  public void init() {
    super.init();
  }

  @Override
  protected void addCredentialsToRequest(HttpUriRequest request) {
    if( isBasicAuthPreemptive() ) {
      HiveDispatchUtils.addCredentialsToRequest(request);
    }
  }

  @Configure
  public void setBasicAuthPreemptive( @Default("false") boolean basicAuthPreemptive ) {
    this.basicAuthPreemptive = basicAuthPreemptive;
  }

  public boolean isBasicAuthPreemptive() {
    return basicAuthPreemptive;
  }

}

