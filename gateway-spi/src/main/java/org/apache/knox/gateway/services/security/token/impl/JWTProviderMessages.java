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
package org.apache.knox.gateway.services.security.token.impl;

import java.text.ParseException;

import org.apache.knox.gateway.i18n.messages.Message;
import org.apache.knox.gateway.i18n.messages.MessageLevel;
import org.apache.knox.gateway.i18n.messages.Messages;
import org.apache.knox.gateway.i18n.messages.StackTrace;

import com.nimbusds.jose.JOSEException;

@Messages(logger="org.apache.knox.gateway")
public interface JWTProviderMessages {

  @Message( level = MessageLevel.DEBUG, text = "Rendering JWT Token for the wire: {0}" )
  void renderingJWTTokenForTheWire(String string);

  @Message( level = MessageLevel.DEBUG, text = "Parsing JWT Token from the wire: {0}" )
  void parsingToken(String wireToken);

  @Message( level = MessageLevel.DEBUG, text = "header: {0}" )
  void printTokenHeader( String header );

  @Message( level = MessageLevel.DEBUG, text = "claims: {0}" )
  void printTokenClaims( String claims );

  @Message( level = MessageLevel.DEBUG, text = "payload: {0}" )
  void printTokenPayload( byte[] payload );

  @Message( level = MessageLevel.FATAL, text = "Unsupported encoding: {0}" )
  void unsupportedEncoding( @StackTrace( level = MessageLevel.DEBUG ) Exception e );

  @Message( level = MessageLevel.ERROR, text = "Unable to parse JWT token: {0}" )
  void unableToParseToken(ParseException e);

  @Message( level = MessageLevel.ERROR, text = "Unable to sign JWT token: {0}" )
  void unableToSignToken(JOSEException e);

  @Message( level = MessageLevel.ERROR, text = "Unable to verify JWT token: {0}" )
  void unableToVerifyToken(JOSEException e);

  @Message( level = MessageLevel.ERROR, text = "Missing claims, expected 6 found : {0}" )
  void missingClaims(int length);
}
