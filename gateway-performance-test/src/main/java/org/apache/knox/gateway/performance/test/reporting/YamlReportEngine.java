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
package org.apache.knox.gateway.performance.test.reporting;

import java.io.IOException;
import java.util.Map;

import org.apache.knox.gateway.performance.test.PerformanceTestConfiguration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

class YamlReportEngine extends AbstractReportEngine {

  private static final String TYPE = "yaml";
  private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

  YamlReportEngine(PerformanceTestConfiguration configuration) throws IOException {
    super(configuration);
  }

  @Override
  public String getContent(Map<String, Object> reportMaterial) throws Exception {
      return mapper.writeValueAsString(reportMaterial);
  }

  @Override
  public String getType() {
    return TYPE;
  }

}
