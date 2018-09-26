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
package org.apache.knox.gateway.filter.rewrite.impl.xml;

/**
 * <pre>
 * {@code
 * <rules>
 *   <filter name="">
 *     <content type="json"> == <scope path="$"/>
 *       <apply/>
 *       <select>
 *         <choice>
 *           <apply/>
 *         </choice>
 *       </select>
 *     </content>
 *   </filter>
 * </rules>
 * }
 * </pre>
 */
public interface XmlRewriteRulesTags {

  static final String ROOT = "rules";

  static final String FUNCTIONS = "functions";

  static final String RULE = "rule";

//  static final String MATCH = "match";
//  static final String CHECK = "check";
//  static final String CONTROL = "control";
//  static final String ACTION = "action";

  static final String FILTER = "filter";
  static final String CONTENT = "content";
  static final String SCOPE = "scope";
  static final String BUFFER = "buffer";
  static final String DETECT = "detect";
  static final String APPLY = "apply";

}
