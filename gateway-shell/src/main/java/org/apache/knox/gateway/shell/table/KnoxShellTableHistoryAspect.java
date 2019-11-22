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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;

/**
 * An AspectJ aspect that intercepts different {@link KnoxShellTable},
 * {@link KnoxShellTableBuilder} and {@link KnoxShellTableFilter} method
 * invocations and records these calls in {@link KnoxShellTableCallHistory}
 */
@Aspect
public class KnoxShellTableHistoryAspect {
  private static final String KNOX_SHELL_TYPE = "org.apache.knox.gateway.shell.table.KnoxShellTable";

  @Pointcut("execution(public org.apache.knox.gateway.shell.table.KnoxShellTableFilter org.apache.knox.gateway.shell.table.KnoxShellTable.filter(..))")
  public void knoxShellTableCreateFilterPointcut() {
  }

  @Pointcut("execution(public * org.apache.knox.gateway.shell.table.*KnoxShellTableBuilder.*(..))")
  public void knoxShellTableBuilderPointcut() {
  }

  @Pointcut("execution(public * org.apache.knox.gateway.shell.table.*KnoxShellTableFilter.*(..))")
  public void knoxShellTableFilterPointcut() {
  }

  @Around("org.apache.knox.gateway.shell.table.KnoxShellTableHistoryAspect.knoxShellTableCreateFilterPointcut()")
  public KnoxShellTableFilter whenCreatingFilter(ProceedingJoinPoint joinPoint) throws Throwable {
    KnoxShellTableFilter filter = null;
    try {
      filter = (KnoxShellTableFilter) joinPoint.proceed();
      return filter;
    } finally {
      if (filter != null) {
        saveKnoxShellTableCall(joinPoint, filter.filteredTable.id);
      }
    }
  }

  @After("org.apache.knox.gateway.shell.table.KnoxShellTableHistoryAspect.knoxShellTableBuilderPointcut()")
  public void afterBuilding(JoinPoint joinPoint) throws Throwable {
    final long builderId = ((KnoxShellTableBuilder) joinPoint.getTarget()).table.id;
    saveKnoxShellTableCall(joinPoint, builderId);
  }

  @After("org.apache.knox.gateway.shell.table.KnoxShellTableHistoryAspect.knoxShellTableFilterPointcut()")
  public void afterFiltering(JoinPoint joinPoint) throws Throwable {
    final long builderId = ((KnoxShellTableFilter) joinPoint.getTarget()).filteredTable.id;
    saveKnoxShellTableCall(joinPoint, builderId);
  }

  private void saveKnoxShellTableCall(JoinPoint joinPoint, long builderId) {
    final Signature signature = joinPoint.getSignature();
    final boolean builderMethod = KNOX_SHELL_TYPE.equals(((MethodSignature) signature).getReturnType().getCanonicalName());
    final Map<Object, Class<?>> params = new HashMap<>();
    Arrays.stream(joinPoint.getArgs()).forEach(param -> params.put(param, param.getClass()));
    KnoxShellTableCallHistory.getInstance().saveCall(builderId, new KnoxShellTableCall(signature.getDeclaringTypeName(), signature.getName(), builderMethod, params));
  }
}
