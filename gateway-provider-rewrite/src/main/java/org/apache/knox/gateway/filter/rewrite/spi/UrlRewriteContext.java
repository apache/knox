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
package org.apache.knox.gateway.filter.rewrite.spi;

import org.apache.knox.gateway.filter.rewrite.api.UrlRewriter;
import org.apache.knox.gateway.util.urltemplate.Evaluator;
import org.apache.knox.gateway.util.urltemplate.Params;
import org.apache.knox.gateway.util.urltemplate.Template;

public interface UrlRewriteContext {

  UrlRewriter.Direction getDirection();

  Template getOriginalUrl();

  Template getCurrentUrl();

  void setCurrentUrl( Template url );

  /**
   * Adds parameters to the rewrite context and replaces some of them if they already exist
   * @param parameters the parameters to be added or replaced
   */
  void addParameters( Params parameters );

  Params getParameters();

  Evaluator getEvaluator();

}
