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
package org.apache.knox.gateway.hadoopauth;

import org.apache.knox.gateway.i18n.messages.Message;
import org.apache.knox.gateway.i18n.messages.MessageLevel;
import org.apache.knox.gateway.i18n.messages.Messages;
import org.apache.knox.gateway.i18n.messages.StackTrace;

@Messages(logger="org.apache.knox.gateway.provider.global.hadoopauth")
public interface HadoopAuthMessages {
  @Message( level = MessageLevel.DEBUG, text = "Hadoop Authentication Asserted Principal: {0}" )
  void hadoopAuthAssertedPrincipal(String name);

  @Message( level = MessageLevel.DEBUG, text = "doAsUser = {0}, RemoteUser = {1} , RemoteAddress = {2}" )
  void hadoopAuthDoAsUser(String doAsUser, String remoteUser, String remoteAddr);

  @Message( level = MessageLevel.DEBUG, text = "Proxy user Authentication successful" )
  void hadoopAuthProxyUserSuccess();

  @Message( level = MessageLevel.ERROR, text = "Proxy user Authentication failed: {0}" )
  void hadoopAuthProxyUserFailed(@StackTrace Throwable t);

  @Message( level = MessageLevel.DEBUG, text = "Initialized the JWT federation filter" )
  void initializedJwtFilter();

  @Message( level = MessageLevel.DEBUG, text = "Using JWT filter to serve the request" )
  void useJwtFilter();

  @Message( level = MessageLevel.INFO, text = "Request {0} matches unauthenticated path list {1}, letting it through" )
  void unauthenticatedPathBypass(String uri, String unauthPathList);

  @Message( level = MessageLevel.ERROR, text = "Error while checking whether path {0} should be allowed unauthenticated access : {1}" )
  void unauthenticatedPathError(String path, String error);

  @Message(level=MessageLevel.WARN, text="{1} alias is NOT stored on neither topology ({0}) nor gateway levels.")
  void noAliasStored(String cluster, String alias);

}
