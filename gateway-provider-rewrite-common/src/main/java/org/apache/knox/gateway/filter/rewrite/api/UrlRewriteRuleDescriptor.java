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
package org.apache.knox.gateway.filter.rewrite.api;

import org.apache.knox.gateway.util.urltemplate.Template;

import java.net.URISyntaxException;
import java.util.EnumSet;

/**
 * <pre>
 * {@code
 *  <rule name="..." pattern="..." dir="request" flow="and"><match></match></rule>
 * }
 * </pre>
 */
public interface UrlRewriteRuleDescriptor extends UrlRewriteFlowDescriptor<UrlRewriteRuleDescriptor> {

  String name();

  UrlRewriteStepDescriptor name( String name );

  String scope();

  UrlRewriteStepDescriptor scope( String scope );

  EnumSet<UrlRewriter.Direction> directions();

  UrlRewriteRuleDescriptor directions( String directions );

  UrlRewriteRuleDescriptor directions( UrlRewriter.Direction... directions );

  String pattern();

  UrlRewriteRuleDescriptor pattern( String pattern ) throws URISyntaxException;

  Template template();

  UrlRewriteRuleDescriptor template( Template pattern );

}
