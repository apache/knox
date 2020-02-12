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
package org.apache.knox.homepage.service.model.druid;

import org.apache.knox.homepage.service.model.ServiceModel;

public class DruidCoordinatorServiceModel extends ServiceModel {
  private static final String SERVICE = "DRUID-COORDINATOR";
  private static final String SHORT_DESCRIPTION = "Druid Coordinator API";
  private static final String DESCRIPTION = "The Druid Coordinator process is primarily responsible for segment management and distribution. "
      + "More specifically, the Druid Coordinator process communicates to Historical processes to load or drop segments based on configurations. "
      + "The Druid Coordinator is responsible for loading new segments, dropping outdated segments, managing segment replication, and balancing segment load.";

  @Override
  public String getServiceName() {
    return SERVICE;
  }

@Override
public String getShortDescription() {
  return SHORT_DESCRIPTION;
}

  @Override
  public String getDescription() {
    return DESCRIPTION;
  }

  @Override
  public Type getType() {
    return Type.API;
  }

}
