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
package org.apache.knox.gateway.filter.rewrite.impl;

import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteRuleDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteStepDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriter;
import org.apache.knox.gateway.filter.rewrite.spi.UrlRewriteFlowDescriptorBase;
import org.apache.knox.gateway.util.urltemplate.Parser;
import org.apache.knox.gateway.util.urltemplate.Template;

import java.net.URISyntaxException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;

public class UrlRewriteRuleDescriptorImpl extends UrlRewriteFlowDescriptorBase<UrlRewriteRuleDescriptor>
    implements UrlRewriteRuleDescriptor {

  private String name;
  private String scope;
  private String pattern;
  private Template template;
  private EnumSet<UrlRewriter.Direction> directions;

  public UrlRewriteRuleDescriptorImpl() {
    super( "rule" );
  }

  @Override
  public String name() {
    return this.name;
  }

  @Override
  public UrlRewriteRuleDescriptor name( String name ) {
    this.name = name;
    return this;
  }

  public void setName( String name ) {
    name( name );
  }

  public String getName() {
    return name;
  }

  public String getScope() {
    return scope;
  }

  public void setScope(String scope) {
    scope( scope );
  }

  @Override
  public String scope() {
    return scope;
  }

  @Override
  public UrlRewriteStepDescriptor scope( String scope ) {
    this.scope = scope;
    return this;
  }

  @Override
  public EnumSet<UrlRewriter.Direction> directions() {
    return directions;
  }

  @Override
  public UrlRewriteRuleDescriptor directions( String directions ) {
    this.directions = parseDirections( directions );
    return this;
  }

  public void setDirections( String directions ) {
    directions( directions );
  }

  public void setDirection( String directions ) {
    directions( directions );
  }

  public void setDir( String directions ) {
    directions( directions );
  }

  public String getDir() {
    String s = null;
    if( directions != null ) {
      StringBuilder sb = new StringBuilder();
      for( UrlRewriter.Direction direction: directions ) {
        if( sb.length() > 0 ) {
          sb.append( ',' );
        }
        sb.append( direction.toString() );
      }
      s = sb.toString();
    }
    return s;
  }

  @Override
  public UrlRewriteRuleDescriptor directions( UrlRewriter.Direction... directions ) {
    return this;
  }

  @Override
  public String pattern() {
    return pattern;
  }

  @Override
  public UrlRewriteRuleDescriptor pattern( String pattern ) throws URISyntaxException {
    this.pattern = pattern;
    this.template = Parser.parseTemplate( pattern );
    return this;
  }

  public void setPattern( String pattern ) throws URISyntaxException {
    pattern( pattern );
  }

  public void setUrl( String pattern ) throws URISyntaxException {
    pattern( pattern );
  }

  public String getPattern() {
    return pattern();
  }

  @Override
  public Template template() {
    return template;
  }

  @Override
  public UrlRewriteRuleDescriptor template( Template template ) {
    this.template = template;
    this.pattern = template.toString();
    return this;
  }

  private static EnumSet<UrlRewriter.Direction> parseDirections( String directions ) {
    EnumSet<UrlRewriter.Direction> set = EnumSet.noneOf( UrlRewriter.Direction.class );
    StringTokenizer parser = new StringTokenizer( directions, " ,;:/|+" );
    while( parser.hasMoreTokens() ) {
      UrlRewriter.Direction direction = parseDirection( parser.nextToken() );
      if( direction != null ) {
        set.add( direction );
      }
    }
    return set;
  }

  private static UrlRewriter.Direction parseDirection( String direction ) {
    direction = direction.trim().toLowerCase( Locale.ROOT );
    return directionNameMap.get( direction );
  }

  private static Map<String,UrlRewriter.Direction> directionNameMap = new HashMap<>();
  static {
    directionNameMap.put( "inbound", UrlRewriter.Direction.IN );
    directionNameMap.put( "in", UrlRewriter.Direction.IN );
    directionNameMap.put( "i", UrlRewriter.Direction.IN );
    directionNameMap.put( "request", UrlRewriter.Direction.IN );
    directionNameMap.put( "req", UrlRewriter.Direction.IN );

    directionNameMap.put( "outbound", UrlRewriter.Direction.OUT );
    directionNameMap.put( "out", UrlRewriter.Direction.OUT );
    directionNameMap.put( "o", UrlRewriter.Direction.OUT );
    directionNameMap.put( "response", UrlRewriter.Direction.OUT );
    directionNameMap.put( "res", UrlRewriter.Direction.OUT );
  }

}
