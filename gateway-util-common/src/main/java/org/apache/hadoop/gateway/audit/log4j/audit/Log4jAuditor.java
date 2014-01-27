/**
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
package org.apache.hadoop.gateway.audit.log4j.audit;



import org.apache.hadoop.gateway.audit.api.AuditContext;
import org.apache.hadoop.gateway.audit.api.AuditService;
import org.apache.hadoop.gateway.audit.api.Auditor;
import org.apache.hadoop.gateway.audit.api.CorrelationContext;
import org.apache.hadoop.gateway.audit.api.CorrelationService;
import org.apache.hadoop.gateway.audit.log4j.correlation.Log4jCorrelationService;
import org.apache.log4j.Logger;
import org.apache.log4j.MDC;

public class Log4jAuditor implements Auditor {

  private Logger logger;
  private String componentName;
  private String serviceName;
  private AuditService auditService = new Log4jAuditService();
  private CorrelationService correlationService = new Log4jCorrelationService();

  public Log4jAuditor( String loggerName, String componentName, String serviceName ) {
    logger = Logger.getLogger( loggerName );
    logger.setAdditivity( false );
    this.componentName = componentName;
    this.serviceName = serviceName;
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
      MDC.put( AuditConstants.MDC_ACTION_KEY, action );
      MDC.put( AuditConstants.MDC_RESOURCE_NAME_KEY, resourceName );
      MDC.put( AuditConstants.MDC_RESOURCE_TYPE_KEY, resourceType );
      MDC.put( AuditConstants.MDC_OUTCOME_KEY, outcome );
      MDC.put( AuditConstants.MDC_SERVICE_KEY, serviceName );
      MDC.put( AuditConstants.MDC_COMPONENT_KEY, componentName );
      
      logger.info( message );
      
      MDC.remove( AuditConstants.MDC_ACTION_KEY );
      MDC.remove( AuditConstants.MDC_RESOURCE_NAME_KEY );
      MDC.remove( AuditConstants.MDC_RESOURCE_TYPE_KEY );
      MDC.remove( AuditConstants.MDC_OUTCOME_KEY );
      MDC.remove( AuditConstants.MDC_SERVICE_KEY );
      MDC.remove( AuditConstants.MDC_COMPONENT_KEY );
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

}
