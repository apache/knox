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

import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteFilterPathDescriptor;
import org.apache.knox.gateway.i18n.resources.Resource;
import org.apache.knox.gateway.i18n.resources.Resources;

@Resources
public interface UrlRewriteResources {

  @Resource( text="No importer for descriptor format {0}" )
  String noImporterForFormat( String format );

  @Resource( text="No exporter for descriptor format {0}" )
  String noExporterForFormat( String format );

  @Resource( text="Unexpected selector type {0}" )
  String unexpectedRewritePathSelector( UrlRewriteFilterPathDescriptor selector );

  @Resource( text="Unexpected selected node type {0}" )
  String unexpectedSelectedNodeType( Object node );

  @Resource( text="Invalid frontend rewrite function parameter {0}" )
  String invalidFrontendFunctionParameter( String parameter );
}
