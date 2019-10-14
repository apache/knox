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
  String ROOT = "rules";
  String FUNCTIONS = "functions";
  String RULE = "rule";

  String FILTER = "filter";
  String CONTENT = "content";
  String SCOPE = "scope";
  String BUFFER = "buffer";
  String DETECT = "detect";
  String APPLY = "apply";
}
