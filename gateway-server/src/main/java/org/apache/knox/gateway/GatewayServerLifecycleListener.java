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
package org.apache.knox.gateway;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.eclipse.jetty.util.component.LifeCycle;

public class GatewayServerLifecycleListener implements LifeCycle.Listener {

  private static final GatewayMessages log = MessagesFactory.get(GatewayMessages.class);

  private static final ThreadLocal<DateFormat> DATE_FORMAT = ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.getDefault()));

  private enum Status {
    STARTING, STARTED, FAILURE, STOPPING, STOPPED
  };

  private final Path lifeCycleFilePath;

  GatewayServerLifecycleListener(GatewayConfig gatewayConfig) throws IOException {
    this.lifeCycleFilePath = Paths.get(gatewayConfig.getGatewayDataDir(), "gatewayServer.status");
    Files.deleteIfExists(lifeCycleFilePath);
    Files.createFile(lifeCycleFilePath);
  }

  @Override
  public void lifeCycleStarting(LifeCycle event) {
    saveStatus(Status.STARTING);
  }

  @Override
  public void lifeCycleStarted(LifeCycle event) {
    saveStatus(Status.STARTED);
  }

  @Override
  public void lifeCycleFailure(LifeCycle event, Throwable cause) {
    saveStatus(Status.FAILURE);
  }

  @Override
  public void lifeCycleStopping(LifeCycle event) {
    saveStatus(Status.STOPPING);
  }

  @Override
  public void lifeCycleStopped(LifeCycle event) {
    saveStatus(Status.STOPPED);
  }

  private void saveStatus(Status status) {
    try {
      // saving the current timestamp in the status file is very useful at debug time
      final String message = DATE_FORMAT.get().format(new Date()) + System.getProperty("line.separator") + status.name() + System.getProperty("line.separator");
      Files.write(lifeCycleFilePath, message.getBytes(StandardCharsets.UTF_8));
    } catch (IOException e) {
      log.failedToSaveGatewayStatus();
    }
  }
}
