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
package org.apache.knox.gateway.config.impl;

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.knox.gateway.config.ConfigurationAdapter;
import org.apache.knox.gateway.config.ConfigurationException;

public class BeanConfigurationAdapter implements ConfigurationAdapter {

  private Object bean;

  public BeanConfigurationAdapter( Object bean ) {
    this.bean = bean;
  }

  @Override
  public Object getConfigurationValue( String name ) throws ConfigurationException {
    try {
      return PropertyUtils.getSimpleProperty( bean, name );
    } catch( Exception e ) {
      throw new ConfigurationException("", e );
    }
  }
}
