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
package org.apache.knox.gateway.i18n.resources;

@Resources( bundle="some.bundle.name" )
public interface ResourcesFormattingSubject {
  @Resource(text="{0}")
  String withAnnotationWithSimplePatternOneParam( int x );

  @Resource(text="before{0}after")
  String withAnnotationWithPatternOneParam( int x );

  @Resource
  String withAnnotationWithoutPatternOneParam( int x );

  String withoutAnnotationsOrParameters();

  String withoutAnnotationsWithOneParam( int x );

  String withoutAnnotationsWithElevenParams( String p1, String p2, String p3, String p4, String p5, String p6, String p7, String p8, String p9, String p10, String p11 );

  @Resource(text="{0},{1}")
  String withMoreFormatParamsThanMethodParams( int x );

  @Resource(text="{0}")
  String withLessFormatParamsThanMethodParams( int x, int y );
}
