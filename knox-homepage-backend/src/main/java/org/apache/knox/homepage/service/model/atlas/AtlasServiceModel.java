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
package org.apache.knox.homepage.service.model.atlas;

import org.apache.knox.homepage.service.model.ServiceModel;

public class AtlasServiceModel extends ServiceModel {
  private static final String SERVICE = "ATLAS";
  private static final String SHORT_DESCRIPTION = "Atlas UI";
  private static final String DESCRIPTION = "Apache Atlas provides open metadata management and governance capabilities for organizations to build a catalog of their data assets, "
      + "classify and govern these assets and provide collaboration capabilities around these data assets for data scientists, analysts and the data governance team.";

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
    return Type.UI;
  }

}
