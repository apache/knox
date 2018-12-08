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

import java.util.Collections;
import java.util.List;

/**
 * This function enhances the 'frontend' function with the ability to add a
 * prefix to the rewritten frontend portion along with the literals
 * provided as an argument.
 *
 * <pre>
 * {@code
 * <div ng-include src=\"'components/navbar/navbar.html?v=1498928142479'\"></div>
 * }
 * </pre>
 *
 * and needs to be rewritten to something like
 *
 * <pre>
 * {@code
 * <div ng-include src=\"'http://localhost:8443/sandbox/service/components/navbar/navbar.html?v=1498928142479'\"></div>
 * }
 * </pre>
 *
 * The rewrite rule could then contain the $prefix function that would delegate
 * to the frontend function.
 *
 * The parameter to the function would be the symbol used as a prefix.
 */
public class HtmlPrefixProcessor
    implements UrlRewriteFunctionProcessor<HtmlPrefixDescriptor> {

  private UrlRewriteFunctionProcessor frontend;

  /**
   * Create an instance
   */
  public HtmlPrefixProcessor() {
    super();
  }

  @Override
  public void initialize(final UrlRewriteEnvironment environment,
      final HtmlPrefixDescriptor descriptor) throws Exception {

    final UrlRewriteFunctionDescriptor frontendDescriptor = UrlRewriteFunctionDescriptorFactory
        .create(FrontendFunctionDescriptor.FUNCTION_NAME);

    frontend = UrlRewriteFunctionProcessorFactory
        .create(FrontendFunctionDescriptor.FUNCTION_NAME, frontendDescriptor);

    frontend.initialize(environment, frontendDescriptor);
  }

  @Override
  public String name() {
    return HtmlPrefixDescriptor.FUNCTION_NAME;
  }

  @Override
  public void destroy() throws Exception {
    frontend.destroy();
  }

  @Override
  public List<String> resolve(UrlRewriteContext context,
      List<String> parameters) throws Exception {
    String prefix = "";

    if ((parameters != null) && (parameters.size() > 1)) {
      prefix = parameters.get(0);
      parameters = parameters.subList(1, parameters.size());
    }

    final List<String> frontendValues = frontend.resolve(context, parameters);

    final StringBuilder buffer = new StringBuilder();
    buffer.append(prefix);
    if (frontendValues != null && !frontendValues.isEmpty()) {
      for (final String value : frontendValues) {
        buffer.append(value);
      }
    }

    return Collections.singletonList(buffer.toString());
  }
}
