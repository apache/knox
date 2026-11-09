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

public interface UrlRewriteFilterApplyDescriptor
    extends UrlRewriteFilterPathDescriptor<UrlRewriteFilterApplyDescriptor> {

  String rule();

  UrlRewriteFilterApplyDescriptor rule( String rule );

  /**
   * By default the path of an apply has to match the whole value it is applied to (attribute value, text node).
   * If find is set the path is a regular expression that is searched for within the value and every match is
   * rewritten on its own, the rest of the value is left untouched.
   */
  boolean find();

  UrlRewriteFilterApplyDescriptor find( boolean find );

}
