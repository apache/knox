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
package org.apache.knox.gateway.ha.provider;

import org.apache.knox.gateway.ha.provider.impl.DefaultURLManager;

import java.util.ServiceLoader;

public class URLManagerLoader {

  public static URLManager loadURLManager(HaServiceConfig config) {
    if (config != null) {
      ServiceLoader<URLManager> loader = ServiceLoader.load(URLManager.class);
      if ( loader != null ) {
        for (URLManager urlManager : loader) {
          if (urlManager.supportsConfig(config)) {
            urlManager.setConfig(config);
            return urlManager;
          }
        }
      }
    }
    return new DefaultURLManager();
  }
}
