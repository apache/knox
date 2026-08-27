/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.trace;

import org.eclipse.jetty.ee8.nested.ErrorHandler;

/**
 * Gateway error handler for generated (Jetty) error pages.
 *
 * <p>Under Jetty 9.4 this class overrode {@code handle(...)} to wrap the
 * servlet {@code HttpServletResponse} in a {@link TraceResponse} so that
 * error page bodies could be captured by the trace-body-status filter. That
 * servlet layer wrap was intentionally dropped in the Jetty 12 migration.
 * Reason:
 * {@link TraceResponse} is now a core {@code org.eclipse.jetty.server.Response}
 * wrapper. EE8 error handler operates on the servlet layer, so the
 * two no longer compose directly. Response body tracing is performed
 * uniformly at the core layer by {@code TraceHandler}.
 */
public class KnoxErrorHandler extends ErrorHandler {

}
