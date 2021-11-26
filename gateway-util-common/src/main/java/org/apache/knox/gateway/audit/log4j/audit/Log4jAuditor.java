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
package org.apache.knox.gateway.audit.log4j.audit;

import org.apache.knox.gateway.audit.api.AuditContext;
import org.apache.knox.gateway.audit.api.AuditService;
import org.apache.knox.gateway.audit.api.Auditor;
import org.apache.knox.gateway.audit.api.CorrelationContext;
import org.apache.knox.gateway.audit.api.CorrelationService;
import org.apache.knox.gateway.audit.log4j.correlation.Log4jCorrelationService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.Logger;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public class Log4jAuditor implements Auditor {

  /** Comma seperated list of query parameters who's values will be masked
  * e.g. -Dmasked_params=knoxtoken,ccNumber
  **/
  public static final String MASKED_QUERY_PARAMS_OPTION = "masked_params";
  private Logger logger;
  private String componentName;
  private String serviceName;
  private AuditService auditService = new Log4jAuditService();
  private CorrelationService correlationService = new Log4jCorrelationService();
  /* List of parameters to be masked */
  private static List<String> maskedParams = new ArrayList<>();

  static {
    /* add defaults */
    maskedParams.add("knoxtoken");
  }

  public Log4jAuditor( String loggerName, String componentName, String serviceName ) {
    logger = (Logger) LogManager.getLogger( loggerName );
    logger.setAdditive(false);
    this.componentName = componentName;
    this.serviceName = serviceName;

    /* check for -Dmasked_params system property for params to mask */
    final String masked_query_params = System.getProperty(MASKED_QUERY_PARAMS_OPTION);
    /* Add the params to mask list */
    if(masked_query_params != null) {
      final String[] params = masked_query_params.split(",");
      for(final String s: params) {
        if(!maskedParams.contains(s)) {
          maskedParams.add(s);
        }
      }
    }
  }

  @Override
  public void audit( CorrelationContext correlationContext, AuditContext auditContext, String action, String resourceName, String resourceType, String outcome, String message ) {
    CorrelationContext previousCorrelationContext = null;
    AuditContext previousAuditContext = null;
    try {
      previousCorrelationContext = correlationService.getContext();
      previousAuditContext = auditService.getContext();
      auditService.attachContext( auditContext );
      correlationService.attachContext( correlationContext );
      auditLog( action, resourceName, resourceType, outcome, message );
    } finally {
      if ( previousAuditContext != null ) {
        auditService.attachContext( previousAuditContext );
      }
      if ( previousCorrelationContext != null ) {
        correlationService.attachContext( previousCorrelationContext );
      }
    }
  }

  @Override
  public void audit( String action, String resourceName, String resourceType, String outcome, String message ) {
    auditLog( action, resourceName, resourceType, outcome, message );
  }

  @Override
  public void audit( String action, String resourceName, String resourceType, String outcome ) {
    auditLog( action, resourceName, resourceType, outcome, null );
  }

  private void auditLog( String action, String resourceName, String resourceType, String outcome, String message ) {
    if ( logger.isInfoEnabled() ) {
      ThreadContext.put( AuditConstants.MDC_ACTION_KEY, action );
      ThreadContext.put( AuditConstants.MDC_RESOURCE_NAME_KEY, maskTokenFromURL(resourceName) );
      ThreadContext.put( AuditConstants.MDC_RESOURCE_TYPE_KEY, resourceType );
      ThreadContext.put( AuditConstants.MDC_OUTCOME_KEY, outcome );
      ThreadContext.put( AuditConstants.MDC_SERVICE_KEY, serviceName );
      ThreadContext.put( AuditConstants.MDC_COMPONENT_KEY, componentName );

      logger.info( message );

      ThreadContext.remove( AuditConstants.MDC_ACTION_KEY );
      ThreadContext.remove( AuditConstants.MDC_RESOURCE_NAME_KEY );
      ThreadContext.remove( AuditConstants.MDC_RESOURCE_TYPE_KEY );
      ThreadContext.remove( AuditConstants.MDC_OUTCOME_KEY );
      ThreadContext.remove( AuditConstants.MDC_SERVICE_KEY );
      ThreadContext.remove( AuditConstants.MDC_COMPONENT_KEY );
    }
  }

  @Override
  public String getComponentName() {
    return componentName;
  }

  @Override
  public String getServiceName() {
    return serviceName;
  }

  @Override
  public String getAuditorName() {
    return logger.getName();
  }

  /**
   * If the url contains knoxtoken parameter, mask it when logging.
   * @param originalUrl original url to try to mask
   * @return originalUrl masking token value
   */
  public static String maskTokenFromURL(final String originalUrl) {
    try {
      final URI original = new URI(originalUrl);

      if( original.getQuery() != null &&
          !original.getQuery().isEmpty()) {

        final String[] query = original.getQuery().split("&");
        final StringBuilder newQuery = new StringBuilder();

        for(int i = 0; i < query.length; i++ ) {

          for(final String s: maskedParams) {
            /* mask "knoxtoken" param */
            if(query[i].contains(s+"=")) {
              newQuery.append(s).append("=***************");
            } else {
              newQuery.append(query[i]);
            }
          }
          if (i < (query.length -1) ) {
            newQuery.append('&');
          }
        }

        final URI newURI = new URI(original.getScheme(), original.getAuthority(),
            original.getPath(), newQuery.toString(), original.getFragment());

        return newURI.toString();
      }

    } catch (final Exception e) {
      // malformed uri just log the original url
    }
    return originalUrl;
  }

}
