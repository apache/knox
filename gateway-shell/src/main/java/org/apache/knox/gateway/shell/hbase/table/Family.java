/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.shell.hbase.table;

import java.util.ArrayList;
import java.util.List;

public class Family<T extends FamilyContainer> {

  private T parent;
  private String name;
  private List<Attribute> attributes = new ArrayList<>();

  public Family( T parent, String name ) {
    this.parent = parent;
    this.name = name;
  }

  public String name() {
    return name;
  }

  public Family<T> attribute( String name, Object value ) {
    attributes.add( new Attribute( name, value ) );
    return this;
  }

  public List<Attribute> attributes() {
    return attributes;
  }

  public T endFamilyDef() {
    return parent;
  }

  public Family<T> family( String name ) {
    Family<T> family = new Family<>( parent, name );
    parent.addFamily( family );
    return family;
  }
}
