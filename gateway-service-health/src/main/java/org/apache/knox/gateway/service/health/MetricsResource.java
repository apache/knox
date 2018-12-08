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
package org.apache.knox.gateway.service.health;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.json.MetricsModule;
import com.codahale.metrics.servlets.MetricsServlet;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;

import javax.annotation.PostConstruct;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;

@Path(MetricsResource.RESOURCE_PATH)
public class MetricsResource {
  static final String VERSION_TAG = "v1";
  static final String RESOURCE_PATH = "/" + VERSION_TAG + "/metrics";
  private static HealthServiceMessages log = MessagesFactory.get(HealthServiceMessages.class);
  public static final String RATE_UNIT = MetricsServlet.class.getCanonicalName() + ".rateUnit";
  public static final String DURATION_UNIT = MetricsServlet.class.getCanonicalName() + ".durationUnit";
  public static final String SHOW_SAMPLES = MetricsServlet.class.getCanonicalName() + ".showSamples";
  public static final String METRICS_REGISTRY = MetricsServlet.class.getCanonicalName() + ".registry";
  public static final String ALLOWED_ORIGIN = MetricsServlet.class.getCanonicalName() + ".allowedOrigin";
  private static final String CONTENT_TYPE = "application/json";

  private String allowedOrigin;
  private transient MetricRegistry registry;
  private transient ObjectMapper mapper;

  @Context
  private HttpServletRequest request;

  @Context
  private HttpServletResponse response;

  @Context
  ServletContext context;

  @PostConstruct
  public void init() {
    if (null == registry) {
      final Object registryAttr = context.getAttribute(METRICS_REGISTRY);
      if (registryAttr instanceof MetricRegistry) {
        this.registry = (MetricRegistry) registryAttr;
      } else {
        throw new IllegalStateException(String.format(Locale.ROOT, "Couldn't find a MetricRegistry instance with key %s",
            METRICS_REGISTRY));
      }
    }
    final TimeUnit rateUnit = parseTimeUnit(context.getInitParameter(RATE_UNIT),
        TimeUnit.SECONDS);
    final TimeUnit durationUnit = parseTimeUnit(context.getInitParameter(DURATION_UNIT),
        TimeUnit.SECONDS);
    final boolean showSamples = Boolean.parseBoolean(context.getInitParameter(SHOW_SAMPLES));
    this.mapper = new ObjectMapper().registerModule(new MetricsModule(rateUnit, durationUnit,
        showSamples));
    this.allowedOrigin = context.getInitParameter(ALLOWED_ORIGIN);
    log.basicInfo(String.format(Locale.ROOT, "Successfully initialized the registry '%s'", METRICS_REGISTRY));
  }

  @GET
  @Produces({APPLICATION_JSON, APPLICATION_XML})
  public Response doGet() {
    return getMetrics();
  }

  @POST
  @Produces({APPLICATION_JSON, APPLICATION_XML})
  public Response doPost() {
    return getMetrics();
  }

  private Response getMetrics() {
    try {
      response.setContentType(CONTENT_TYPE);
      if (allowedOrigin != null) {
        response.setHeader("Access-Control-Allow-Origin", allowedOrigin);
      }
      response.setHeader("Cache-Control", "must-revalidate,no-cache,no-store");
      response.setStatus(HttpServletResponse.SC_OK);

      try (OutputStream output = response.getOutputStream()) {
        getWriter(request).writeValue(output, registry);
      }
    } catch (IOException ioe) {
      log.logException("metrics", ioe);
      return Response.serverError().entity(String.format(Locale.ROOT, "Failed to reply correctly due to : %s ", ioe)).build();
    }
    return Response.ok().build();
  }

  private ObjectWriter getWriter(HttpServletRequest request) {
    final boolean prettyPrint = Boolean.parseBoolean(request.getParameter("pretty"));
    if (prettyPrint) {
      return mapper.writerWithDefaultPrettyPrinter();
    }
    return mapper.writer();
  }


  TimeUnit parseTimeUnit(String value, TimeUnit defaultValue) {
    try {
      return TimeUnit.valueOf(String.valueOf(value).toUpperCase(Locale.US));
    } catch (IllegalArgumentException e) {
      return defaultValue;
    }
  }
}
