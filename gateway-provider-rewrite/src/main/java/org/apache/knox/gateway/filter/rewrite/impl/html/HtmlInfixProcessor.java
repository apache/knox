/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership.  The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.filter.rewrite.impl.html;

import org.apache.knox.gateway.filter.rewrite.api.FrontendFunctionDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteEnvironment;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteFunctionDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteFunctionDescriptorFactory;
import org.apache.knox.gateway.filter.rewrite.impl.UrlRewriteFunctionProcessorFactory;
import org.apache.knox.gateway.filter.rewrite.spi.UrlRewriteContext;
import org.apache.knox.gateway.filter.rewrite.spi.UrlRewriteFunctionProcessor;

import java.util.Arrays;
import java.util.List;

/**
 * This function enhances the 'frontend' function with the ability to add a
 * prefix and post-fix to the rewritten frontend.
 *
 * The rewrite rule could then contain the $infix function that would delegate
 * to the frontend function.
 * e.g. {$infix[pre,url,post]}
 * where
 * pre = prefix
 * post = postfix
 * url = frontend url (resolved by Knox)
 * The parameter to the function would be the symbol used as a prefix.
 */

public class HtmlInfixProcessor
    implements UrlRewriteFunctionProcessor<HtmlInfixDescriptor> {

  private UrlRewriteFunctionProcessor frontend;

  /**
   * Create an instance
   */
  public HtmlInfixProcessor() {
    super();
  }

  @Override
  public void initialize(final UrlRewriteEnvironment environment,
      final HtmlInfixDescriptor descriptor) throws Exception {

    final UrlRewriteFunctionDescriptor frontendDescriptor = UrlRewriteFunctionDescriptorFactory
        .create(FrontendFunctionDescriptor.FUNCTION_NAME);

    frontend = UrlRewriteFunctionProcessorFactory
        .create(FrontendFunctionDescriptor.FUNCTION_NAME, frontendDescriptor);

    frontend.initialize(environment, frontendDescriptor);
  }

  @Override
  public String name() {
    return HtmlInfixDescriptor.FUNCTION_NAME;
  }

  @Override
  public void destroy() throws Exception {
    frontend.destroy();
  }

  @Override
  public List<String> resolve(UrlRewriteContext context,
      List<String> parameters) throws Exception {
    String prefix = "";
    String postfix = "";

    if ((parameters != null) && (parameters.size() > 1)) {
      prefix = parameters.get(0);
      postfix = parameters.get(parameters.size() - 1);
      parameters = parameters.subList(1, parameters.size()-1);
    }

    final List<String> frontendValues = frontend.resolve(context, parameters);

    final StringBuffer buffer = new StringBuffer();
    buffer.append(prefix);
    if (frontendValues != null && frontendValues.size() > 0) {
      for (final String value : frontendValues) {
        buffer.append(value);
      }
    }
    buffer.append(postfix);

    return Arrays.asList(buffer.toString());
  }
}
