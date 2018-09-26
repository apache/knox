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
package org.apache.knox.gateway.descriptor;

import javax.servlet.Filter;
import java.util.List;

public interface FilterDescriptor {

  ResourceDescriptor up();

  FilterDescriptor name( String name );

  String name();

  FilterDescriptor role( String role );

  String role();

  FilterDescriptor impl( String impl );

  FilterDescriptor impl( Class<? extends Filter> type );

  String impl();

  List<FilterParamDescriptor> params();

  FilterParamDescriptor param();

  FilterParamDescriptor createParam();

  FilterDescriptor param( FilterParamDescriptor param );

  FilterDescriptor params( List<FilterParamDescriptor> params );

}
