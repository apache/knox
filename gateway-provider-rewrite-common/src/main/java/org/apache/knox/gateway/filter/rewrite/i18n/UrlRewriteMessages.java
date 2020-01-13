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
package org.apache.knox.gateway.filter.rewrite.i18n;

import org.apache.knox.gateway.filter.rewrite.api.UrlRewriter;
import org.apache.knox.gateway.filter.rewrite.spi.UrlRewriteStepStatus;
import org.apache.knox.gateway.i18n.messages.Message;
import org.apache.knox.gateway.i18n.messages.MessageLevel;
import org.apache.knox.gateway.i18n.messages.Messages;
import org.apache.knox.gateway.i18n.messages.StackTrace;
import org.apache.knox.gateway.util.urltemplate.Template;

@Messages(logger="org.apache.knox.gateway")
public interface UrlRewriteMessages {

  @Message( level = MessageLevel.DEBUG, text = "Failed to parse value as URL: {0}" )
  void failedToParseValueForUrlRewrite( String value );

  @Message( level = MessageLevel.ERROR, text = "Failed to write the rules descriptor: {0}" )
  void failedToWriteRulesDescriptor( @StackTrace( level = MessageLevel.DEBUG ) Exception e );

  @Message( level = MessageLevel.DEBUG, text = "Failed to filter attribute {0}: {1}" )
  void failedToFilterAttribute( String attributeName, @StackTrace( level = MessageLevel.DEBUG ) Exception e );

  @Message( level = MessageLevel.ERROR, text = "Failed to load rewrite rules descriptor: {0}" )
  void failedToLoadRewriteRulesDescriptor( @StackTrace( level = MessageLevel.DEBUG ) Exception e );

  @Message( level = MessageLevel.ERROR, text = "Failed to initialize rewrite rules: {0}" )
  void failedToInitializeRewriteRules( @StackTrace( level = MessageLevel.DEBUG ) Exception e );

  @Message( level = MessageLevel.ERROR, text = "Failed to initialize rewrite functions: {0}" )
  void failedToInitializeRewriteFunctions( @StackTrace( level = MessageLevel.DEBUG ) Exception e );

  @Message( level = MessageLevel.ERROR, text = "Failed to destroy rewrite rule processor: {0}" )
  void failedToDestroyRewriteStepProcessor( @StackTrace(level = MessageLevel.DEBUG) Exception e );

  @Message( level = MessageLevel.ERROR, text = "Failed to destroy rewrite function processor: {0}" )
  void failedToDestroyRewriteFunctionProcessor( @StackTrace( level = MessageLevel.DEBUG ) Exception e );

  @Message( level = MessageLevel.ERROR, text = "Failed to filter value {0}, rule {1}" )
  void failedToFilterValue( String value, String rule );

  @Message( level = MessageLevel.ERROR, text = "Failed to filter value {0}, rule {1}: {2}" )
  void failedToFilterValue( String value, String rule, @StackTrace( level = MessageLevel.DEBUG ) Exception e );

  @Message( level = MessageLevel.ERROR, text = "Failed to filter field name {0}: {1}" )
  void failedToFilterFieldName( String fieldName, @StackTrace( level = MessageLevel.DEBUG ) Exception e );

  @Message( level = MessageLevel.ERROR, text = "Rewrite function {0} failed: {1}" )
  void failedToInvokeRewriteFunction( String functionName, @StackTrace( level = MessageLevel.DEBUG ) Exception e );

  @Message( level = MessageLevel.ERROR, text = "Failed to find values by parameter name {0}: {1}" )
  void failedToFindValuesByParameter( String parameterName, @StackTrace( level = MessageLevel.DEBUG ) Exception e );

  @Message( level = MessageLevel.DEBUG, text = "Rewrote URL: {0}, direction: {1} via implicit rule: {2} to URL: {3}" )
  void rewroteUrlViaImplicitRule( Template inputUri, UrlRewriter.Direction direction, String ruleName, Template outputUri );

  @Message( level = MessageLevel.DEBUG, text = "Rewrote URL: {0}, direction: {1} via explicit rule: {2} to URL: {3}" )
  void rewroteUrlViaExplicitRule( Template inputUri, UrlRewriter.Direction direction, String ruleName, Template outputUri );

  @Message( level = MessageLevel.TRACE, text = "Failed to rewrite URL: {0}, direction: {1} via rule: {2}, status: {3}" )
  void failedToRewriteUrl( Template inputUri, UrlRewriter.Direction direction, String ruleName, UrlRewriteStepStatus stepStatus );

  @Message( level = MessageLevel.ERROR, text = "Failed to rewrite URL: {0}, direction: {1}, rule: {2}" )
  void failedToRewriteUrlDueToException( Template inputUri, UrlRewriter.Direction direction, String ruleName, @StackTrace(level = MessageLevel.DEBUG) Exception exception );

  @Message( level = MessageLevel.TRACE, text = "No rule matching URL: {0}, direction: {1}" )
  void noRuleMatchingUrl( Template inputUri, UrlRewriter.Direction direction );

  @Message( level = MessageLevel.TRACE, text = "Failed to decode query string: {0}" )
  void failedToDecodeQueryString( String queryString, @StackTrace(level = MessageLevel.TRACE) Exception exception );

  @Message( level = MessageLevel.DEBUG, text = "No rewrite rule was found, skipping rewriting JSON request body" )
  void skippingRewritingJsonRequestBody();
}
