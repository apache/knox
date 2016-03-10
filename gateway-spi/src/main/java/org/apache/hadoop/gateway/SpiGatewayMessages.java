/**
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
package org.apache.hadoop.gateway;

import org.apache.hadoop.gateway.i18n.messages.Message;
import org.apache.hadoop.gateway.i18n.messages.MessageLevel;
import org.apache.hadoop.gateway.i18n.messages.Messages;
import org.apache.hadoop.gateway.i18n.messages.StackTrace;

import java.net.URI;
import java.nio.charset.Charset;


/**
 *
 */
@Messages(logger="org.apache.hadoop.gateway")
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
}
