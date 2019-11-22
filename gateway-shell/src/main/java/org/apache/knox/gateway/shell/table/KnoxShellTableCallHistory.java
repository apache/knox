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
package org.apache.knox.gateway.shell.table;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A wrapper class to maintain the chain of builder/filter call invocations
 * which resulted in a {@link KnoxShellTable} being built
 *
 * The following useful functions are exposed:
 * <ul>
 * <li>replay: a {@link KnoxShellTable} can be built by replaying a previously saved
 * call history</li>
 * <li>rollback: any {@link KnoxShellTable} can be rolled back to it's previous valid
 * state (if any)</li>
 * </ul>
 */
class KnoxShellTableCallHistory {

  private static final KnoxShellTableCallHistory INSTANCE = new KnoxShellTableCallHistory();
  private final Map<Long, List<KnoxShellTableCall>> callHistory = new ConcurrentHashMap<>();

  private KnoxShellTableCallHistory() {
  }

  static KnoxShellTableCallHistory getInstance() {
    return INSTANCE;
  }

  void saveCall(long id, KnoxShellTableCall call) {
    saveCalls(id, Collections.singletonList(call));
  }

  void saveCalls(long id, List<KnoxShellTableCall> calls) {
    if (!callHistory.containsKey(id)) {
      callHistory.put(id, new LinkedList<>());
    }
    callHistory.get(id).addAll(calls);
  }

  void removeCallsById(long id) {
    callHistory.remove(id);
  }

  public List<KnoxShellTableCall> getCallHistory(long id) {
    return callHistory.containsKey(id) ? Collections.unmodifiableList(callHistory.get(id)) : Collections.emptyList();
  }

  /**
   * Rolls back the given table to its previous valid state. This means the table
   * can be rolled back if there is any previous (i.e. not the last one) step in
   * its call history that produces a {@link KnoxShellTable}
   *
   * @param id
   *          the table to apply the rollback operation on
   * @return the previous valid state of the table identified by <code>id</code>
   * @throws IllegalArgumentException
   *           if the rollback operation is not permitted
   *
   */
  KnoxShellTable rollback(long id) {
    final AtomicInteger counter = new AtomicInteger(1);
    final List<Integer> validSteps = new ArrayList<>();
    getCallHistory(id).forEach(call -> {
      int step = counter.getAndIncrement();
      if (call.isBuilderMethod()) {
        validSteps.add(step);
      }
    });
    if (validSteps.size() <= 1) {
      throw new IllegalArgumentException("There is no valid step to be rollback to");
    }
    return replay(id, validSteps.get(validSteps.size() - 2));
  }

  /**
   * Tries to replay the previously saved call history of the given table.
   *
   * @param id
   *          the table to apply the replay operation on
   * @param step
   *          the step up to where call history should be replayed
   * @return the {@link KnoxShellTable} as a result of the previously saved call
   *         invocations
   * @throws IllegalArgumentException
   *           if the the given call indicated by the given step does not produce
   *           a {@link KnoxShellTable}
   */
  KnoxShellTable replay(long id, int step) {
    final List<KnoxShellTableCall> callHistory = getCallHistory(id);
    validateReplayStep(step, callHistory);
    Object callResult = KnoxShellTable.builder();
    for (int counter = 0; counter < step; counter++) {
      callResult = invokeCall(callResult, callHistory.get(counter));
    }
    return (KnoxShellTable) callResult;
  }

  private void validateReplayStep(int step, List<KnoxShellTableCall> callHistory) {
    final AtomicInteger counter = new AtomicInteger(1);
    callHistory.forEach(call -> {
      if (counter.getAndIncrement() == step && !call.isBuilderMethod()) {
        throw new IllegalArgumentException(
            String.format(Locale.getDefault(), "It is not allowed to replay up to step %d as this step does not produce an intance of KnoxShellTable", step));
      }
    });
  }

  private Object invokeCall(Object callResult, KnoxShellTableCall call) {
    try {
      final Class<?> invokerClass = Class.forName(call.getInvokerClass());
      final Class<?>[] parameterTypes = call.getParameterTypes();
      final Method method = invokerClass.getMethod(call.getMethod(), parameterTypes);
      final Object[] params = new Object[call.getParams().size()];
      final AtomicInteger index = new AtomicInteger(0);
      for (Map.Entry<Object, Class<?>> param : call.getParams().entrySet()) {
        params[index.getAndIncrement()] = param.getValue().cast(param.getKey());
      }
      return method.invoke(callResult, params);
    } catch (Exception e) {
      throw new IllegalArgumentException("Error while processing " + call, e);
    }
  }

}
