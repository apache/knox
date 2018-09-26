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
package org.apache.knox.gateway.audit.log4j.correlation;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.concurrent.Callable;

import org.apache.knox.gateway.audit.api.CorrelationContext;
import org.apache.knox.gateway.audit.api.CorrelationService;
import org.apache.log4j.MDC;

public class Log4jCorrelationService implements CorrelationService {
  
  public static final String MDC_CORRELATION_CONTEXT_KEY = "correlation_context";
  
  @Override
  public CorrelationContext createContext() {
    CorrelationContext context = getContext();
    if ( context == null ) {
      context = new Log4jCorrelationContext();
      attachContext( context );
    }
    return context;
  }

  @Override
  public CorrelationContext getContext() {
    return (CorrelationContext) MDC.get( MDC_CORRELATION_CONTEXT_KEY );
  }

  @Override
  public void attachContext( CorrelationContext context ) {
    if ( context != null ) {
      MDC.put( MDC_CORRELATION_CONTEXT_KEY, context );
    }
  }

  @Override
  public CorrelationContext detachContext() {
    CorrelationContext context = (CorrelationContext) MDC.get( MDC_CORRELATION_CONTEXT_KEY );
    MDC.remove( MDC_CORRELATION_CONTEXT_KEY );
    return context;
  }
  
  @Override
  public <T> T execute( CorrelationContext context, Callable<T> callable ) throws Exception {
    try {
      attachContext( context );
      return callable.call();
    } finally {
      detachContext();
    }
  }

  @Override
  public CorrelationContext attachExternalizedContext(byte[] externalizedContext) {
    CorrelationContext context = readExternalizedContext( externalizedContext );
    attachContext( context );
    return context;
  }

  @Override
  public byte[] detachExternalizedContext() {
    byte[] result = getExternalizedContext();
    detachContext();
    return result;
  }

  @Override
  public CorrelationContext readExternalizedContext(byte[] externalizedContext) {
    ByteArrayInputStream bais = new ByteArrayInputStream( externalizedContext );
    ObjectInput oi = null;
    CorrelationContext context = null;
    try {
      oi = new ObjectInputStream( bais );
      context = (CorrelationContext) oi.readObject();
    } catch ( IOException e ) {
      throw new IllegalArgumentException( e );
    } catch ( ClassNotFoundException e ) {
      throw new IllegalArgumentException( e );
    } finally {
      try {
        bais.close();
        if ( oi != null) {
          oi.close();
        }
      } catch ( IOException e ) {
        throw new IllegalArgumentException( e );
      }
    }
    return context;
  }

  @Override
  public byte[] getExternalizedContext() {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try {
    ObjectOutputStream oos = new ObjectOutputStream( baos );
    oos.writeObject( getContext() );
    oos.close();
    } catch ( IOException e ) {
      throw new RuntimeException( e );
    }
    return baos.toByteArray();
  }
  
}

