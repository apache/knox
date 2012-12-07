/**
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
package org.apache.hadoop.gateway.i18n.messages;

import static org.apache.hadoop.gateway.i18n.messages.MessageLevel.ERROR;
import static org.apache.hadoop.gateway.i18n.messages.MessageLevel.INFO;

/**
 *
 */
@Messages( bundle="some.bundle.name", logger="some.logger.name", codes="ID:{0}" )
public interface MessagesTestSubject {

  @Message( level=ERROR, code=3, text="p0={0}" )
  void withFullAnnotationAndParameter( int x );

  @Message( level=INFO, code=42, text="str={0}, t={1}" )
  void withEverything( String str, @StackTrace(level=INFO) Throwable t );

  @Message
  void withoutParams();

  void withoutAnnotations( int x );

  @Message
  void withoutStackTrace( Throwable t );

  @Message
  void withMismatchedText();

}
