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
package org.apache.knox.gateway.hostmap.api;

import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteFunctionDescriptor;

public class HostmapFunctionDescriptor implements
    UrlRewriteFunctionDescriptor<HostmapFunctionDescriptor> {

  public static final String FUNCTION_NAME = "hostmap";

  private String configLocation;

  @Override
  public String name() {
    return FUNCTION_NAME;
  }

  public HostmapFunctionDescriptor config( String configLocation ) {
    this.configLocation = configLocation;
    return this;
  }

  public String config() {
    return configLocation;
  }

  public String getConfig() {
    return config();
  }

  public void setConfig( String configLocation ) {
    config( configLocation );
  }

}
