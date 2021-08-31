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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.performance.test.PerformanceTestConfiguration;
import org.apache.knox.gateway.performance.test.PerformanceTestMessages;

abstract class AbstractReportEngine implements ReportEngine {
  private static final PerformanceTestMessages LOG = MessagesFactory.get(PerformanceTestMessages.class);
  private final DateFormat REPORT_FILE_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.ROOT);
  private final PerformanceTestConfiguration configuration;
  private final Path reportFolderPath;

  AbstractReportEngine(PerformanceTestConfiguration configuration) throws IOException {
    this.configuration = configuration;
    this.reportFolderPath = Paths.get(configuration.getReportingTargetFolder(), getType());
    if (!reportFolderPath.toFile().exists()) {
      Files.createDirectories(reportFolderPath);
    }
  }

  @Override
  public void generateReport(String reportName, Map<String, Object> reportMaterial) {
    if (configuration.isReportingEngineEnabled(getType())) {
      String reportFile = reportName + "." + REPORT_FILE_DATE_FORMAT.format(new Date()) + "." + getType();
      try {
        FileUtils.writeStringToFile(reportFolderPath.resolve(reportFile).toFile(), getContent(reportMaterial), StandardCharsets.UTF_8);
      } catch (Exception e) {
        LOG.failedToGenerateReport(getType(), e.getMessage(), e);
      }
    }
  }

  public abstract String getContent(Map<String, Object> reportMaterial) throws Exception;

  public abstract String getType();

}
