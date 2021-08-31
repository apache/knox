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

import org.apache.http.cookie.Cookie;
import org.apache.knox.gateway.i18n.messages.Message;
import org.apache.knox.gateway.i18n.messages.MessageLevel;
import org.apache.knox.gateway.i18n.messages.Messages;
import org.apache.knox.gateway.i18n.messages.StackTrace;

import java.net.URI;
import java.nio.charset.Charset;



@Messages(logger="org.apache.knox.gateway")
public interface SpiGatewayMessages {

  @Message( level = MessageLevel.DEBUG, text = "Dispatch request: {0} {1}" )
  void dispatchRequest( String method, URI uri );

  @Message( level = MessageLevel.WARN, text = "Connection exception dispatching request: {0} {1}" )
  void dispatchServiceConnectionException( URI uri, @StackTrace(level=MessageLevel.WARN) Exception e );

  @Message( level = MessageLevel.DEBUG, text = "Dispatch response status: {0}" )
  void dispatchResponseStatusCode(int statusCode);

  @Message( level = MessageLevel.DEBUG, text = "Dispatch response status: {0}, Location: {1}" )
  void dispatchResponseCreatedStatusCode( int statusCode, String location );

  @Message( level = MessageLevel.DEBUG, text = "Successful Knox->Hadoop SPNegotiation authentication for URL: {0}" )
  void successfulSPNegoAuthn(String uri);

  @Message( level = MessageLevel.ERROR, text = "Failed Knox->Hadoop SPNegotiation authentication for URL: {0}" )
  void failedSPNegoAuthn(String uri);

  @Message( level = MessageLevel.WARN, text = "Error occurred when closing HTTP client : {0}" )
  void errorClosingHttpClient(@StackTrace(level=MessageLevel.WARN) Exception e);

  @Message( level = MessageLevel.WARN, text = "Skipping unencodable parameter {0}={1}, {2}: {3}" )
  void skippingUnencodableParameter( String name, String value, String encoding, @StackTrace( level = MessageLevel.DEBUG ) Exception e );

  @Message( level = MessageLevel.DEBUG, text = "Using default character set {1} for entity of type {0}" )
  void usingDefaultCharsetForEntity( String entityMimeType, String defaultCharset );

  @Message( level = MessageLevel.DEBUG, text = "Using explicit character set {1} for entity of type {0}" )
  void usingExplicitCharsetForEntity( String mimeType, Charset charset );

  @Message( level = MessageLevel.DEBUG, text = "Inbound response entity content type not provided." )
  void unknownResponseEntityContentType();

  @Message( level = MessageLevel.DEBUG, text = "Inbound response entity content type: {0}" )
  void inboundResponseEntityContentType( String fullContentType );

  @Message( level = MessageLevel.WARN, text = "Possible identity spoofing attempt - impersonation parameter removed: {0}" )
  void possibleIdentitySpoofingAttempt( String impersonationParam );

  @Message( level = MessageLevel.WARN, text = "Error ocurred while accessing params in query string: {0}" )
  void unableToGetParamsFromQueryString(@StackTrace(level=MessageLevel.WARN) Exception e);

  @Message( level = MessageLevel.DEBUG, text = "Accepting service cookie: {0}" )
  void acceptingServiceCookie( Cookie cookie );

  @Message( level = MessageLevel.ERROR, text = "Error reading Kerberos login configuration {0} : {1}" )
  void errorReadingKerberosLoginConfig(String fileName, @StackTrace(level=MessageLevel.ERROR) Exception e);

  @Message( level = MessageLevel.INFO,
            text = "Applying a derived dispatch whitelist because none is configured in gateway-site: {0}" )
  void derivedDispatchWhitelist(String derivedWhitelist);

  @Message( level=MessageLevel.ERROR,
             text = "Unable to reliably determine the Knox domain for the default whitelist. Defaulting to allow requests only to {0}. Please consider explicitly configuring the whitelist via the gateway.dispatch.whitelist property in gateway-site" )
  void unableToDetermineKnoxDomainForDefaultWhitelist(String permittedHostName);

  @Message( level = MessageLevel.ERROR,
            text = "The dispatch to {0} was disallowed because it fails the dispatch whitelist validation. See documentation for dispatch whitelisting." )
  void dispatchDisallowed(String uri);

  @Message( level = MessageLevel.DEBUG, text = "HTTP client connection timeout is set to {0} for {1}" )
  void setHttpClientConnectionTimeout(int connectionTimeout, String serviceRole);

  @Message( level = MessageLevel.DEBUG, text = "HTTP client socket timeout is set to {0} for {1}" )
  void setHttpClientSocketTimeout(int csocketTimeout, String serviceRole);

  @Message( level = MessageLevel.DEBUG, text = "replayBufferSize is set to {0} for {1}" )
  void setReplayBufferSize(int replayBufferSize, String serviceRole);

  @Message( level = MessageLevel.DEBUG, text = "Using two way SSL in {0}" )
  void usingTwoWaySsl(String serviceRole);

  @Message( level = MessageLevel.DEBUG, text = "Adding outbound header {0} and value {1}" )
  void addedOutboundheader(String header, String value);

  @Message( level = MessageLevel.DEBUG, text = "Skipped adding outbound header {0} and value {1}" )
  void skippedOutboundHeader(String header, String value);
}
