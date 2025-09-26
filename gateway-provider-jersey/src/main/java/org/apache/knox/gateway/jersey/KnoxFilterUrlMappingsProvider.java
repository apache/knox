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
package org.apache.knox.gateway.jersey;

import org.glassfish.jersey.servlet.spi.FilterUrlMappingsProvider;
import javax.servlet.FilterConfig;
import java.util.Collections;
import java.util.List;

/**
 * Custom FilterUrlMappingsProvider for Knox that handles null FilterRegistration objects
 * gracefully. Jersey 2.47+ expects FilterRegistration objects to be available in the
 * ServletContext, but Knox's dynamic filter loading doesn't always provide them.
 * This provider returns empty collections to prevent NullPointerExceptions.
 */
public class KnoxFilterUrlMappingsProvider implements FilterUrlMappingsProvider {
    @Override
    public List<String> getFilterUrlMappings(FilterConfig filterConfig) {
        // Return empty list instead of trying to access potentially null FilterRegistration
        // This prevents the NullPointerException that occurs in Jersey's FilterUrlMappingsProviderImpl
        return Collections.emptyList();
    }
}
