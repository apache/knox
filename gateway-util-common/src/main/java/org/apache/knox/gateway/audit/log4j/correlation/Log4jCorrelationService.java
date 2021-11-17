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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.concurrent.Callable;

import org.apache.knox.gateway.audit.api.CorrelationContext;
import org.apache.knox.gateway.audit.api.CorrelationService;
import org.apache.logging.log4j.ThreadContext;

public class Log4jCorrelationService implements CorrelationService {
  public static final String KEY_CORRELATION_CONTEXT = "correlation_context";
  public static final String KEY_ROOT_REQUEST_ID = KEY_CORRELATION_CONTEXT + "_rootRequestId";
  public static final String KEY_PARENT_REQUEST_ID = KEY_CORRELATION_CONTEXT + "_parentRequestId";
  public static final String KEY_REQUEST_ID = KEY_CORRELATION_CONTEXT + "_requestId";

  @Override
  public CorrelationContext getContext() {
    if (ThreadContext.get(KEY_CORRELATION_CONTEXT) == null) {
      return null;
    }
    return new Log4jCorrelationContext(
              ThreadContext.get(KEY_REQUEST_ID), ThreadContext.get(KEY_PARENT_REQUEST_ID), ThreadContext.get(KEY_ROOT_REQUEST_ID));
  }

  @Override
  public void attachContext(CorrelationContext context) {
    if (context != null) {
      ThreadContext.put(KEY_CORRELATION_CONTEXT, "true");
      ThreadContext.put(KEY_REQUEST_ID, context.getRequestId());
      ThreadContext.put(KEY_PARENT_REQUEST_ID, context.getParentRequestId());
      ThreadContext.put(KEY_ROOT_REQUEST_ID, context.getRootRequestId());
    }
  }

  @Override
  public void detachContext() {
    ThreadContext.remove(KEY_CORRELATION_CONTEXT);
    ThreadContext.remove(KEY_REQUEST_ID);
    ThreadContext.remove(KEY_PARENT_REQUEST_ID);
    ThreadContext.remove(KEY_ROOT_REQUEST_ID);
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
        oos.writeObject(getContext());
      }
      return baos.toByteArray();
    } catch ( IOException e ) {
      throw new RuntimeException( e );
    }
  }
}

