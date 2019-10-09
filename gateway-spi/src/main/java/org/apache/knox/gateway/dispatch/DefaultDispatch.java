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
package org.apache.knox.gateway.dispatch;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ContentType;
import org.apache.knox.gateway.SpiGatewayMessages;
import org.apache.knox.gateway.SpiGatewayResources;
import org.apache.knox.gateway.audit.api.Action;
import org.apache.knox.gateway.audit.api.ActionOutcome;
import org.apache.knox.gateway.audit.api.AuditServiceFactory;
import org.apache.knox.gateway.audit.api.Auditor;
import org.apache.knox.gateway.audit.api.ResourceType;
import org.apache.knox.gateway.audit.log4j.audit.AuditConstants;
import org.apache.knox.gateway.config.Configure;
import org.apache.knox.gateway.config.Default;
import org.apache.knox.gateway.config.GatewayConfig;
import org.apache.knox.gateway.i18n.messages.MessagesFactory;
import org.apache.knox.gateway.i18n.resources.ResourcesFactory;
import org.apache.knox.gateway.util.MimeTypes;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class DefaultDispatch extends AbstractGatewayDispatch {

  protected static final String SET_COOKIE = "SET-COOKIE";
  protected static final String WWW_AUTHENTICATE = "WWW-AUTHENTICATE";

  protected static final SpiGatewayMessages LOG = MessagesFactory.get(SpiGatewayMessages.class);
  protected static final SpiGatewayResources RES = ResourcesFactory.get(SpiGatewayResources.class);
  protected static final Auditor auditor = AuditServiceFactory.getAuditService().getAuditor(AuditConstants.DEFAULT_AUDITOR_NAME,
      AuditConstants.KNOX_SERVICE_NAME, AuditConstants.KNOX_COMPONENT_NAME);

  protected static final String EXCLUDE_ALL = "*";
  private Set<String> outboundResponseExcludeHeaders = new HashSet<>(Arrays.asList(WWW_AUTHENTICATE));
  private Set<String> outboundResponseExcludedSetCookieHeaderDirectives = new HashSet<>(Arrays.asList(EXCLUDE_ALL));

  //Buffer size in bytes
  private int replayBufferSize = -1;

  @Override
  public void destroy() {

  }

  protected int getReplayBufferSize() {
    if (replayBufferSize > 0) {
      return Math.abs(replayBufferSize/1024);
    }
    return replayBufferSize;
  }

  @Configure
  protected void setReplayBufferSize(@Default("-1")int size) {
    setReplayBufferSizeInBytes(size);
  }

  protected int getReplayBufferSizeInBytes() {
    return replayBufferSize;
  }

  protected void setReplayBufferSizeInBytes(int size) {
    if (size > 0) {
      size *= 1024;
    }
    replayBufferSize = size;
  }


  protected void executeRequest(
         HttpUriRequest outboundRequest,
         HttpServletRequest inboundRequest,
         HttpServletResponse outboundResponse)
         throws IOException {
      HttpResponse inboundResponse = executeOutboundRequest(outboundRequest);
      writeOutboundResponse(outboundRequest, inboundRequest, outboundResponse, inboundResponse);
   }

  protected HttpResponse executeOutboundRequest( HttpUriRequest outboundRequest ) throws IOException {
    LOG.dispatchRequest( outboundRequest.getMethod(), outboundRequest.getURI() );
    HttpResponse inboundResponse;

    try {
      auditor.audit( Action.DISPATCH, outboundRequest.getURI().toString(), ResourceType.URI, ActionOutcome.UNAVAILABLE, RES.requestMethod( outboundRequest.getMethod() ) );
      if( !Boolean.parseBoolean(System.getProperty(GatewayConfig.HADOOP_KERBEROS_SECURED))) {
        // Hadoop cluster not Kerberos enabled
        addCredentialsToRequest( outboundRequest );
      }
      inboundResponse = getHttpClient().execute( outboundRequest );

      int statusCode = inboundResponse.getStatusLine().getStatusCode();
      if( statusCode != 201 ) {
        LOG.dispatchResponseStatusCode( statusCode );
      } else {
        Header location = inboundResponse.getFirstHeader( "Location" );
        if( location == null ) {
          LOG.dispatchResponseStatusCode( statusCode );
        } else {
          LOG.dispatchResponseCreatedStatusCode( statusCode, location.getValue() );
        }
      }
      auditor.audit( Action.DISPATCH, outboundRequest.getURI().toString(), ResourceType.URI, ActionOutcome.SUCCESS, RES.responseStatus( statusCode ) );
    } catch( Exception e ) {
      // We do not want to expose back end host. port end points to clients, see JIRA KNOX-58
      auditor.audit( Action.DISPATCH, outboundRequest.getURI().toString(), ResourceType.URI, ActionOutcome.FAILURE );
      LOG.dispatchServiceConnectionException( outboundRequest.getURI(), e );
      throw new IOException( RES.dispatchConnectionError() );
    }
    return inboundResponse;
  }

  protected void writeOutboundResponse(HttpUriRequest outboundRequest, HttpServletRequest inboundRequest, HttpServletResponse outboundResponse, HttpResponse inboundResponse) throws IOException {
    // Copy the client respond header to the server respond.
    outboundResponse.setStatus(inboundResponse.getStatusLine().getStatusCode());
    copyResponseHeaderFields(outboundResponse, inboundResponse);

    HttpEntity entity = inboundResponse.getEntity();
    if( entity != null ) {
      outboundResponse.setContentType( getInboundResponseContentType( entity ) );
      //KM[ If this is set here it ends up setting the content length to the content returned from the server.
      // This length might not match if the the content is rewritten.
      //      long contentLength = entity.getContentLength();
      //      if( contentLength <= Integer.MAX_VALUE ) {
      //        outboundResponse.setContentLength( (int)contentLength );
      //      }
      //]
      InputStream stream = entity.getContent();
      try {
        writeResponse( inboundRequest, outboundResponse, stream );
      } finally {
        closeInboundResponse( inboundResponse, stream );
      }
    }
  }

  private String getInboundResponseContentType( final HttpEntity entity ) {
    String fullContentType = null;
    if( entity != null ) {
      ContentType entityContentType = ContentType.get( entity );
      if( entityContentType != null ) {
        if( entityContentType.getCharset() == null ) {
          final String entityMimeType = entityContentType.getMimeType();
          final String defaultCharset = MimeTypes.getDefaultCharsetForMimeType( entityMimeType );
          if( defaultCharset != null ) {
            LOG.usingDefaultCharsetForEntity( entityMimeType, defaultCharset );
            entityContentType = entityContentType.withCharset( defaultCharset );
          }
        } else {
          LOG.usingExplicitCharsetForEntity( entityContentType.getMimeType(), entityContentType.getCharset() );
        }
        fullContentType = entityContentType.toString();
      }
    }
    if( fullContentType == null ) {
      LOG.unknownResponseEntityContentType();
    } else {
      LOG.inboundResponseEntityContentType( fullContentType );
    }
    return fullContentType;
  }

  protected void closeInboundResponse( HttpResponse response, InputStream stream ) throws IOException {
    try {
      stream.close();
    } finally {
      if( response instanceof Closeable ) {
        ( (Closeable)response).close();
      }
    }
  }

   /**
    * This method provides a hook for specialized credential propagation
    * in subclasses.
    *
    * @param outboundRequest outboundRequest to add credentials to
    */
   protected void addCredentialsToRequest(HttpUriRequest outboundRequest) {
   }

   protected HttpEntity createRequestEntity(HttpServletRequest request)
         throws IOException {

      String contentType = request.getContentType();
      int contentLength = request.getContentLength();
      InputStream contentStream = request.getInputStream();

      HttpEntity entity;
      if (contentType == null) {
         entity = new InputStreamEntity(contentStream, contentLength);
      } else {
         entity = new InputStreamEntity(contentStream, contentLength, ContentType.parse(contentType));
      }
      GatewayConfig config =
         (GatewayConfig)request.getServletContext().getAttribute( GatewayConfig.GATEWAY_CONFIG_ATTRIBUTE );
      if( config != null && config.isHadoopKerberosSecured() ) {
        //Check if delegation token is supplied in the request
        boolean delegationTokenPresent = false;
        String queryString = request.getQueryString();
        if (queryString != null) {
          delegationTokenPresent = queryString.startsWith("delegation=") || queryString.contains("&delegation=");
        }
        if (replayBufferSize < 0) {
          replayBufferSize = config.getHttpServerRequestBuffer();
        }
        if (!delegationTokenPresent && replayBufferSize > 0 ) {
          entity = new PartiallyRepeatableHttpEntity(entity, replayBufferSize);
        }
      }

      return entity;
   }

   @Override
   public void doGet(URI url, HttpServletRequest request, HttpServletResponse response)
         throws IOException, URISyntaxException {
      HttpGet method = new HttpGet(url);
      // https://issues.apache.org/jira/browse/KNOX-107 - Service URLs not rewritten for WebHDFS GET redirects
      // This is now taken care of in DefaultHttpClientFactory.createHttpClient
      // and setting params here causes configuration setup there to be ignored there.
      // method.getParams().setBooleanParameter("http.protocol.handle-redirects", false);
      copyRequestHeaderFields(method, request);
      executeRequest(method, request, response);
   }

   @Override
   public void doOptions(URI url, HttpServletRequest request, HttpServletResponse response)
         throws IOException, URISyntaxException {
      HttpOptions method = new HttpOptions(url);
      executeRequest(method, request, response);
   }

   @Override
   public void doPut(URI url, HttpServletRequest request, HttpServletResponse response)
         throws IOException, URISyntaxException {
      HttpPut method = new HttpPut(url);
      HttpEntity entity = createRequestEntity(request);
      method.setEntity(entity);
      copyRequestHeaderFields(method, request);
      executeRequest(method, request, response);
   }

   @Override
   public void doPatch(URI url, HttpServletRequest request, HttpServletResponse response)
         throws IOException, URISyntaxException {
      HttpPatch method = new HttpPatch(url);
      HttpEntity entity = createRequestEntity(request);
      method.setEntity(entity);
      copyRequestHeaderFields(method, request);
      executeRequest(method, request, response);
   }

   @Override
   public void doPost(URI url, HttpServletRequest request, HttpServletResponse response)
         throws IOException, URISyntaxException {
      HttpPost method = new HttpPost(url);
      HttpEntity entity = createRequestEntity(request);
      method.setEntity(entity);
      copyRequestHeaderFields(method, request);
      executeRequest(method, request, response);
   }

   @Override
   public void doDelete(URI url, HttpServletRequest request, HttpServletResponse response)
         throws IOException, URISyntaxException {
      HttpDelete method = new HttpDelete(url);
      copyRequestHeaderFields(method, request);
      executeRequest(method, request, response);
   }

  @Override
  public void doHead(URI url, HttpServletRequest request, HttpServletResponse response)
      throws IOException, URISyntaxException {
    final HttpHead method = new HttpHead(url);
    copyRequestHeaderFields(method, request);
    executeRequest(method, request, response);
  }

  public void copyResponseHeaderFields(HttpServletResponse outboundResponse, HttpResponse inboundResponse) {
    final Map<String, Set<String>> excludedHeaderDirectives = getOutboundResponseExcludeHeaders().stream().collect(Collectors.toMap(k -> k, v -> new HashSet<>(Arrays.asList(EXCLUDE_ALL))));
    excludedHeaderDirectives.put(SET_COOKIE, getOutboundResponseExcludedSetCookieHeaderDirectives());

    for ( Header header : inboundResponse.getAllHeaders() ) {
      final String responseHeaderValue = calculateResponseHeaderValue(header, excludedHeaderDirectives);
      if (responseHeaderValue.isEmpty()) {
        continue;
      }
      outboundResponse.addHeader(header.getName(), responseHeaderValue);
    }
  }

  private String calculateResponseHeaderValue(Header headerToCheck, Map<String, Set<String>> excludedHeaderDirectives) {
    final String headerNameToCheck = headerToCheck.getName();
    if (excludedHeaderDirectives != null && excludedHeaderDirectives.containsKey(headerNameToCheck.toUpperCase(Locale.ROOT))) {
      final Set<String> excludedHeaderValues = excludedHeaderDirectives.get(headerNameToCheck.toUpperCase(Locale.ROOT));
      if (!excludedHeaderValues.isEmpty()) {
        if (excludedHeaderValues.stream().filter(e -> e.equals(EXCLUDE_ALL)).findAny().isPresent()) {
          return ""; // we should exclude all -> there should not be any value added with this header
        } else {
          final String separator = SET_COOKIE.equalsIgnoreCase(headerNameToCheck) ? "; " : " ";
          Set<String> headerValuesToCheck = new HashSet<>(Arrays.asList(headerToCheck.getValue().trim().split("\\s+")));
          headerValuesToCheck = headerValuesToCheck.stream().map(h -> h.replaceAll(separator.trim(), "")).collect(Collectors.toSet());
          headerValuesToCheck.removeAll(excludedHeaderValues);
          return headerValuesToCheck.isEmpty() ? "" : headerValuesToCheck.stream().collect(Collectors.joining(separator));
        }
      }
    }

    return headerToCheck.getValue();
  }

  public Set<String> getOutboundResponseExcludeHeaders() {
    return outboundResponseExcludeHeaders == null ? Collections.emptySet() : outboundResponseExcludeHeaders;
  }

  public Set<String> getOutboundResponseExcludedSetCookieHeaderDirectives() {
    return outboundResponseExcludedSetCookieHeaderDirectives == null ? Collections.emptySet() : outboundResponseExcludedSetCookieHeaderDirectives;
  }
}
