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
package org.apache.knox.gateway.filter.rewrite.impl.json;

import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteFilterContentDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriter;
import org.apache.knox.gateway.filter.rewrite.i18n.UrlRewriteMessages;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.util.urltemplate.Parser;
import org.apache.knox.gateway.util.urltemplate.Resolver;
import org.apache.knox.gateway.util.urltemplate.Template;

import java.io.IOException;
import java.io.Reader;
import java.net.URISyntaxException;

public class JsonUrlRewriteFilterReader extends JsonFilterReader {
  private static final UrlRewriteMessages LOG = MessagesFactory.get( UrlRewriteMessages.class );

  private Resolver resolver;
  private UrlRewriter rewriter;
  private UrlRewriter.Direction direction;

  public JsonUrlRewriteFilterReader(
      Reader reader,
      UrlRewriter rewriter,
      Resolver resolver,
      UrlRewriter.Direction direction,
      UrlRewriteFilterContentDescriptor config )
          throws IOException {
    super( reader, config );
    this.resolver = resolver;
    this.rewriter = rewriter;
    this.direction = direction;
  }

  @Override
  protected String filterValueString(String name, String value, String rule ) {
    /*
     Prevent inbound JSON payload from getting rewritten
     (by picking up best match rewrite rule) when no rule is
     specified for the request url. This behavior does not affect outbound
     JSON requests and any other rewrite functionality.
     */
    if(rule != null || UrlRewriter.Direction.OUT == direction) {
      try {
        Template input = Parser.parseLiteral( value );
        Template output = rewriter.rewrite( resolver, input, direction, rule );
        // We should only use output if it is valid. If a rule doesn't match output could be null.
        if(output != null) {
          value = output.getPattern();
        }
      } catch( URISyntaxException e ) {
        LOG.failedToParseValueForUrlRewrite( value );
      }
      return value;
    } else {
      LOG.skippingRewritingJsonRequestBody();
      return value;
    }
  }
}
