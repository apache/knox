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

import org.apache.knox.gateway.i18n.messages.Message;
import org.apache.knox.gateway.i18n.messages.MessageLevel;
import org.apache.knox.gateway.i18n.messages.Messages;
import org.apache.knox.gateway.i18n.messages.StackTrace;

@Messages(logger="org.apache.knox.gateway")
public interface ShiroMessages {
  @Message( level = MessageLevel.INFO, text = "Request {0} matches unauthenticated path configured in topology, letting it through" )
  void unauthenticatedPathBypass(String uri);

  @Message( level = MessageLevel.WARN, text = "Invalid URL pattern for rule: {0}" )
  void invalidURLPattern(String rule);

  @Message( level = MessageLevel.TRACE, text = "Acquiring EhcacheShiro instance named {0}" )
  void acquireEhcacheShiro(String name);

  @Message( level = MessageLevel.INFO, text = "Cache with name {0} does not yet exist.  Creating now." )
  void noCacheFound(String name);

  @Message( level = MessageLevel.INFO, text = "Added EhcacheShiro named {0}" )
  void ehcacheShiroAdded(String name);

  @Message( level = MessageLevel.INFO, text = "Using existing EhcacheShiro named {0}" )
  void usingExistingEhcacheShiro(String name);

  @Message( level = MessageLevel.WARN, text = "The Shiro managed CacheManager threw an Exception while closing: {0}" )
  void errorClosingManagedCacheManager(@StackTrace(level=MessageLevel.WARN) Exception e);
}
