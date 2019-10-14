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

import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteFilterContentDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteFilterDescriptor;
import org.apache.knox.gateway.util.MimeTypeMap;

import javax.activation.MimeType;
import javax.activation.MimeTypeParseException;
import java.util.ArrayList;
import java.util.List;

public class UrlRewriteFilterDescriptorImpl implements
    UrlRewriteFilterDescriptor {

  private String name;
  private List<UrlRewriteFilterContentDescriptor> contentList = new ArrayList<>();
  private MimeTypeMap<UrlRewriteFilterContentDescriptor> contentMap = new MimeTypeMap<>();

  public UrlRewriteFilterDescriptorImpl() {
  }

  @Override
  public String name() {
    return this.name;
  }

  public String getName() {
    return name;
  }

  @Override
  public UrlRewriteFilterDescriptor name( String name ) {
    this.name = name;
    return this;
  }

  public void setName( String name ) {
    this.name = name;
  }

  @Override
  public List<UrlRewriteFilterContentDescriptor> getContents() {
    return contentList;
  }

  @Override
  public UrlRewriteFilterContentDescriptor getContent( String type ) {
    return contentMap.get( type );
  }

  @Override
  public UrlRewriteFilterContentDescriptor getContent( MimeType type ) {
    return contentMap.get( type );
  }

  @Override
  public UrlRewriteFilterContentDescriptor addContent( String type ) {
    UrlRewriteFilterContentDescriptorImpl impl = new UrlRewriteFilterContentDescriptorImpl();
    impl.type( type );
    contentList.add( impl );
    try {
      contentMap.put( new MimeType( type ), impl );
    } catch( MimeTypeParseException e ) {
      throw new IllegalArgumentException( type, e );
    }
    return impl;
  }

}
