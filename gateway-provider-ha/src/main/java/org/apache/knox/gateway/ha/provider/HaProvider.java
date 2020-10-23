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

import java.util.List;

public interface HaProvider {

  HaDescriptor getHaDescriptor();

  /**
   * Add a service name (role) as a HA service with the URLs that it is configured for
   *
   * @param serviceName the name of the service
   * @param urls        the list of urls that can be used for that service
   */
  void addHaService(String serviceName, List<String> urls);

  /**
   * Returns whether the service is enabled for HA
   *
   * @param serviceName the name of the service
   * @return true if the service is enabled; false otherwise
   */
  boolean isHaEnabled(String serviceName);

  /**
   * Returns the current URL that is known to be active for the service
   *
   * @param serviceName the name of the service
   * @return the URL as a string or null if the service name is not found
   */
  String getActiveURL(String serviceName);

  /**
   * Sets a given URL that is known to be active for the service
   *
   * @param serviceName the name of the service
   * @param url         the active url
   */
  void setActiveURL(String serviceName, String url);

  /**
   * Mark the URL for the service as one that has failed. This method puts changes the active URL to
   * the next available URL for the service.
   *
   * @param serviceName the name of the service
   * @param url         the URL that has failed in some way
   */
  void markFailedURL(String serviceName, String url);

  /**
   * This method puts changes the active URL to
   * the next available URL for the service.
   *
   * @param serviceName the name of the service
   */
  void makeNextActiveURLAvailable(String serviceName);

  /**
   * This method puts gets all the currently
   * available URLs for the service.
   *
   * @param serviceName the name of the service
   */
  List<String> getURLs(String serviceName);
}
