/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
 * This function enhances the 'frontend' function with the ability to add a prefix to the rewritten frontend portion
 * along with the '@import' literal. This is a workaround for the requirement to provide the ability to rewrite
 * a portion of html content that contains a tag like the following
 *
 * <pre>
 * {@code
 * <head> <style type=\"text/css\">@import "pretty.css";</style></head>
 * }
 * </pre>
 *
 * and needs to be rewritten to something like
 *
 * <pre>
 * {@code
 * <head> <style type=\"text/css\">@import "http://localhost:8443/sandbox/service/pretty.css";</style></head>
 * }
 * </pre>
 *
 * The rewrite rule could then contain the $import function that would delegate to the frontend function.
 *
 * If there are more than one params passed, the first one is used as a prefix to the value of the frontend function.
 *
 */
public class HtmlImportFunctionProcessor implements UrlRewriteFunctionProcessor<HtmlImportFunctionDescriptor> {

  private static final String IMPORT_LITERAL = "@import";

  private UrlRewriteFunctionProcessor frontend;

  @Override
  public void initialize(UrlRewriteEnvironment environment, HtmlImportFunctionDescriptor descriptor) throws Exception {
    UrlRewriteFunctionDescriptor frontendDescriptor = UrlRewriteFunctionDescriptorFactory
        .create(FrontendFunctionDescriptor.FUNCTION_NAME);
    frontend = UrlRewriteFunctionProcessorFactory.create(FrontendFunctionDescriptor.FUNCTION_NAME, frontendDescriptor);
    frontend.initialize(environment, frontendDescriptor);
  }

  @Override
  public void destroy() throws Exception {
    frontend.destroy();
  }

  @Override
  public List<String> resolve(UrlRewriteContext context, List<String> parameters) throws Exception {
    String prefix = "";
    if ( parameters != null && parameters.size() > 1 ) {
      prefix = parameters.get(0);
      parameters = parameters.subList(1, parameters.size());
    }
    List<String> frontendValues = frontend.resolve(context, parameters);
    StringBuffer buffer = new StringBuffer(IMPORT_LITERAL);
    buffer.append(" ");
    buffer.append(prefix);
    if ( frontendValues != null && !frontendValues.isEmpty() ) {
      for ( String value : frontendValues ) {
        buffer.append(value);
      }
    }
    return Arrays.asList(buffer.toString());
  }

  @Override
  public String name() {
    return HtmlImportFunctionDescriptor.FUNCTION_NAME;
  }

}
