/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.ha.provider;

public interface HaServiceConfig {

   public void setServiceName(String name);

   public String getServiceName();

   public boolean isEnabled();

   public void setEnabled(boolean enabled);

   public void setMaxFailoverAttempts(int limit);

   public int getMaxFailoverAttempts();

   public void setFailoverSleep(int sleep);

   public int getFailoverSleep();

   public void setMaxRetryAttempts(int limit);

   public int getMaxRetryAttempts();

   public void setRetrySleep(int sleep);

   public int getRetrySleep();

   public String getZookeeperEnsemble();

   public void setZookeeperEnsemble(String zookeeperEnsemble);

   public String getZookeeperNamespace();

   public void setZookeeperNamespace(String zookeeperNamespace);
}
