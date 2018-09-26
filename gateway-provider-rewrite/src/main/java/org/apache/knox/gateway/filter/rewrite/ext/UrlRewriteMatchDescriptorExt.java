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
package org.apache.knox.gateway.filter.rewrite.ext;

import org.apache.knox.gateway.filter.rewrite.spi.UrlRewriteFlowDescriptorBase;
import org.apache.knox.gateway.util.urltemplate.Parser;
import org.apache.knox.gateway.util.urltemplate.Template;

import java.net.URISyntaxException;

public class UrlRewriteMatchDescriptorExt
    extends UrlRewriteFlowDescriptorBase<UrlRewriteMatchDescriptor>
    implements UrlRewriteMatchDescriptor {

  private String operation;
  private String pattern;
  private Template template;

  public UrlRewriteMatchDescriptorExt() {
    super( "match" );
  }

  @Override
  public String operation() {
    return operation;
  }

  public String getOperation() {
    return operation;
  }

  @Override
  public UrlRewriteMatchDescriptor operation( String operation ) {
    this.operation = operation;
    return this;
  }

  public void setOperation( String operation ) {
    operation( operation );
  }

  public void setOper( String operation ) {
    operation( operation );
  }

  public void setOp( String operation ) {
    operation( operation );
  }

  public String getOper() {
    return operation();
  }

  @Override
  public String pattern() {
    return pattern;
  }

  @Override
  public UrlRewriteMatchDescriptor pattern( String pattern ) throws URISyntaxException {
    this.pattern = pattern;
    this.template = Parser.parseTemplate( pattern );
    return this;
  }

  public void setUrl( String pattern ) throws URISyntaxException {
    pattern( pattern );
  }

  public void setPattern( String pattern ) throws URISyntaxException {
    pattern( pattern );
  }

  public String getPattern() {
    return pattern;
  }

  @Override
  public Template template() {
    return template;
  }

  @Override
  public UrlRewriteMatchDescriptor template( Template template ) {
    this.template = template;
    // The template is now optional for rules.
    if( template == null ) {
      this.pattern = null;
    } else {
      this.pattern = template.toString();
    }
    return this;
  }

}
