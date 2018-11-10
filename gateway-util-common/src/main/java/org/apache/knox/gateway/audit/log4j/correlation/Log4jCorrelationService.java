/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.audit.log4j.correlation;

import org.apache.knox.gateway.audit.api.CorrelationContext;
import org.apache.knox.gateway.audit.api.CorrelationService;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.LogEvent;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Map;
import java.util.concurrent.Callable;

public class Log4jCorrelationService implements CorrelationService {
  public static final String MDC_CORRELATION_CONTEXT_KEY = "correlation_context";

  public static CorrelationContext createContext(LogEvent event) {
    if (event == null) {
      return null;
    }

    Map<String, String> data = event.getContextData().toMap();
    return new Log4jCorrelationContext(
        data.get(MDC_CORRELATION_CONTEXT_KEY + "_requestId"),
        data.get(MDC_CORRELATION_CONTEXT_KEY + "_parentRequestId"),
        data.get(MDC_CORRELATION_CONTEXT_KEY + "_rootRequestId")
    );
  }

  @Override
  public CorrelationContext createContext() {
    Log4jCorrelationContext context = new Log4jCorrelationContext();
    attachContext(context);
    return context;
  }

  @Override
  public CorrelationContext getContext() {
    if (ThreadContext.get(MDC_CORRELATION_CONTEXT_KEY) == null) {
      return null;
    }

    return new Log4jCorrelationContext(
        ThreadContext.get(MDC_CORRELATION_CONTEXT_KEY + "_requestId"),
        ThreadContext.get(MDC_CORRELATION_CONTEXT_KEY + "_parentRequestId"),
        ThreadContext.get(MDC_CORRELATION_CONTEXT_KEY + "_rootRequestId")
    );
  }

  @Override
  public void attachContext(CorrelationContext context) {
    if (context != null) {
      ThreadContext.put(MDC_CORRELATION_CONTEXT_KEY, "true");
      ThreadContext.put(MDC_CORRELATION_CONTEXT_KEY + "_requestId", context.getRequestId());
      ThreadContext.put(MDC_CORRELATION_CONTEXT_KEY + "_parentRequestId", context.getParentRequestId());
      ThreadContext.put(MDC_CORRELATION_CONTEXT_KEY + "_rootRequestId", context.getRootRequestId());
    }
  }

  @Override
  public CorrelationContext detachContext() {
    CorrelationContext context = getContext();
    ThreadContext.remove(MDC_CORRELATION_CONTEXT_KEY);
    ThreadContext.remove(MDC_CORRELATION_CONTEXT_KEY + "_requestId");
    ThreadContext.remove(MDC_CORRELATION_CONTEXT_KEY + "_parentRequestId");
    ThreadContext.remove(MDC_CORRELATION_CONTEXT_KEY + "_rootRequestId");
    return context;
  }

  @Override
  public <T> T execute(CorrelationContext context, Callable<T> callable) throws Exception {
    try {
      attachContext(context);
      return callable.call();
    } finally {
      detachContext();
    }
  }

  @Override
  public CorrelationContext attachExternalizedContext(byte[] externalizedContext) {
    CorrelationContext context = readExternalizedContext(externalizedContext);
    attachContext(context);
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
    try (ByteArrayInputStream bais = new ByteArrayInputStream( externalizedContext );
         ObjectInput oi = new ObjectInputStream( bais )) {
      return (CorrelationContext) oi.readObject();
    } catch ( IOException | ClassNotFoundException e ) {
      throw new IllegalArgumentException( e );
    }
  }

  @Override
  public byte[] getExternalizedContext() {
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
      try(ObjectOutputStream oos = new ObjectOutputStream( baos )) {
        oos.writeObject( getContext() );
      }
      return baos.toByteArray();
    } catch ( IOException e ) {
      throw new RuntimeException( e );
    }
  }
}

