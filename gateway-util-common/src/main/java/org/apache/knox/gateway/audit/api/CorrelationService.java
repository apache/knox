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
package org.apache.knox.gateway.audit.api;

import java.util.concurrent.Callable;

/**
 * Manipulates the correlations context associated with the current thread.
 */
public interface CorrelationService {
  /**
   * Returns the current attached correlation context if any.
   *
   * @return The current attached correlation context.  May be null.
   */
  CorrelationContext getContext();

  /**
   * Sets the current attached correlation context.
   * Will overwrite any existing attached context if any.
   * Null contexts are ignored and any existing attached context will remain.
   *
   * @param context The correlation context to attach.
   */
  void attachContext( CorrelationContext context );

  /**
   * Detaches the existing attached context if any.
   * This will typically be done so that the context can be persisted or attached to a different thread.
   */
  void detachContext();

  /**
   * Executes the callable within the provided correlation context.
   * The provided context is attached and detached around the invocation of the callable.
   * @param context The correlation context to establish around the invocation of the callable.  May not be null.
   * @param callable The callable to invoke after establishing the correlation context.  May not be null.
   * @param <T> Type of callable
   * @return The result of the callable's call method.
   * @throws Exception Thrown if thrown by the callable's call method.
   */
  <T> T execute( CorrelationContext context, Callable<T> callable ) throws Exception;

  /**
   * Attaches the externalized correlation context
   * @param externalizedContext The externalized correlation context
   * @return An attached instance of correlation context that was restored form externalized context
   */
  CorrelationContext attachExternalizedContext( byte[] externalizedContext );

  /**
   * Detaches the existing attached correlation context and returns it in externalized form.
   * @return The detached externalized context
   */
  byte[] detachExternalizedContext();

  /**
   * Restores correlation context from externalized form.
   * @param externalizedContext The externalized correlation context. May not be null.
   * @return the correlation context that is not attached yet
   */
  CorrelationContext readExternalizedContext( byte[] externalizedContext );

  /**
   * Returns externalized correlation context without detaching it from execution scope.
   * @return The externalized correlation context
   */
  byte[] getExternalizedContext();

}
