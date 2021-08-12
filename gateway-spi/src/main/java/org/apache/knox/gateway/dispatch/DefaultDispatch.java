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

import static java.util.stream.Collectors.toCollection;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
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
import org.apache.knox.gateway.config.Optional;
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
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.LinkedHashSet;

public class DefaultDispatch extends AbstractGatewayDispatch {
  protected static final String SET_COOKIE = "SET-COOKIE";
  protected static final String WWW_AUTHENTICATE = "WWW-AUTHENTICATE";
  /* list of cookies that should be blocked when set-cookie header is allowed */
  protected static final Set<String> EXCLUDE_SET_COOKIES_DEFAULT = new HashSet<>(Arrays.asList("hadoop.auth", "hive.server2.auth", "impala.auth"));


  protected static final SpiGatewayMessages LOG = MessagesFactory.get(SpiGatewayMessages.class);
  protected static final SpiGatewayResources RES = ResourcesFactory.get(SpiGatewayResources.class);
  protected static final Auditor auditor = AuditServiceFactory.getAuditService().getAuditor(AuditConstants.DEFAULT_AUDITOR_NAME,
      AuditConstants.KNOX_SERVICE_NAME, AuditConstants.KNOX_COMPONENT_NAME);

  protected static final String EXCLUDE_ALL = "*";
  private Set<String> outboundResponseExcludeHeaders = Collections.singleton(WWW_AUTHENTICATE);
  private Set<String> outboundResponseExcludedSetCookieHeaderDirectives = Collections.singleton(EXCLUDE_ALL);

  @Optional
  @Configure
  private String serviceRole;

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

  public String getServiceRole() {
    return serviceRole;
  }

  public void setServiceRole(String serviceRole) {
    this.serviceRole = serviceRole;
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
    LOG.setReplayBufferSize(replayBufferSize, getServiceRole());
  }

  /**
   * Wrapper around execute request to accommodate any
   * request processing such as additional HA logic.
   * @param outboundRequest
   * @param inboundRequest
   * @param outboundResponse
   * @throws IOException
   */
  protected void executeRequestWrapper(HttpUriRequest outboundRequest,
      HttpServletRequest inboundRequest, HttpServletResponse outboundResponse)
      throws IOException {
    executeRequest(outboundRequest, inboundRequest, outboundResponse);
  }

  /**
   * A outbound response wrapper used by classes extending this class
   * to modify any outgoing
   * response i.e. cookies
   */
  protected void outboundResponseWrapper(final HttpUriRequest outboundRequest, final HttpServletRequest inboundRequest, HttpServletResponse outboundResponse) {
    /* no-op */
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
    /* in case any changes to outbound response are needed */
    outboundResponseWrapper(outboundRequest, inboundRequest, outboundResponse);
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

  protected String getInboundResponseContentType( final HttpEntity entity ) {
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

   protected HttpEntity createRequestEntity(HttpServletRequest request) throws IOException {
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
         throws IOException {
      HttpGet method = new HttpGet(url);
      // https://issues.apache.org/jira/browse/KNOX-107 - Service URLs not rewritten for WebHDFS GET redirects
      // This is now taken care of in DefaultHttpClientFactory.createHttpClient
      // and setting params here causes configuration setup there to be ignored there.
      // method.getParams().setBooleanParameter("http.protocol.handle-redirects", false);
      copyRequestHeaderFields(method, request);
     executeRequestWrapper(method, request, response);
   }

   @Override
   public void doOptions(URI url, HttpServletRequest request, HttpServletResponse response)
         throws IOException {
      HttpOptions method = new HttpOptions(url);
     executeRequestWrapper(method, request, response);
   }

   @Override
   public void doPut(URI url, HttpServletRequest request, HttpServletResponse response)
         throws IOException {
      HttpPut method = new HttpPut(url);
      HttpEntity entity = createRequestEntity(request);
      method.setEntity(entity);
      copyRequestHeaderFields(method, request);
     executeRequestWrapper(method, request, response);
   }

   @Override
   public void doPatch(URI url, HttpServletRequest request, HttpServletResponse response)
         throws IOException {
      HttpPatch method = new HttpPatch(url);
      HttpEntity entity = createRequestEntity(request);
      method.setEntity(entity);
      copyRequestHeaderFields(method, request);
     executeRequestWrapper(method, request, response);
   }

   @Override
   public void doPost(URI url, HttpServletRequest request, HttpServletResponse response)
         throws IOException, URISyntaxException {
      HttpPost method = new HttpPost(url);
      HttpEntity entity = createRequestEntity(request);
      method.setEntity(entity);
      copyRequestHeaderFields(method, request);
     executeRequestWrapper(method, request, response);
   }

   @Override
   public void doDelete(URI url, HttpServletRequest request, HttpServletResponse response)
         throws IOException {
      HttpDelete method = new HttpDelete(url);
      copyRequestHeaderFields(method, request);
     executeRequestWrapper(method, request, response);
   }

  @Override
  public void doHead(URI url, HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    final HttpHead method = new HttpHead(url);
    copyRequestHeaderFields(method, request);
    executeRequestWrapper(method, request, response);
  }

  public void copyResponseHeaderFields(HttpServletResponse outboundResponse, HttpResponse inboundResponse) {
    final TreeMap<String, Set<String>> excludedHeaderDirectives = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    getOutboundResponseExcludeHeaders().stream().forEach(excludeHeader ->
        excludedHeaderDirectives.put(excludeHeader, Collections.singleton(EXCLUDE_ALL)));
    excludedHeaderDirectives.put(SET_COOKIE, getOutboundResponseExcludedSetCookieHeaderDirectives());

    for (Header header : inboundResponse.getAllHeaders()) {
      boolean isBlockedAuthHeader = Arrays.stream(header.getElements()).anyMatch(h -> EXCLUDE_SET_COOKIES_DEFAULT.contains(h.getName()) && getOutboundResponseExcludedSetCookieHeaderDirectives().contains(h.getName()) );
      /* in case auth header is blocked blocked the entire set-cookie part */
      if(!isBlockedAuthHeader) {
            final String responseHeaderValue = calculateResponseHeaderValue(header, excludedHeaderDirectives);
            if (responseHeaderValue.isEmpty()) {
                continue;
            }
            outboundResponse.addHeader(header.getName(), responseHeaderValue);
            LOG.addedOutboundheader(header.getName(), responseHeaderValue);
      } else {
            LOG.skippedOutboundHeader(header.getName(), header.getValue());
      }
    }
  }

  private String calculateResponseHeaderValue(Header headerToCheck, Map<String, Set<String>> excludedHeaderDirectives) {
    final String headerNameToCheck = headerToCheck.getName();
    if (excludedHeaderDirectives != null && excludedHeaderDirectives.containsKey(headerNameToCheck)) {
      final Set<String> excludedHeaderValues = excludedHeaderDirectives.get(headerNameToCheck);
      if (!excludedHeaderValues.isEmpty()) {
        if (excludedHeaderValues.stream().anyMatch(e -> e.equals(EXCLUDE_ALL))) {
          return ""; // we should exclude all -> there should not be any value added with this header
        } else {
          final String separator = SET_COOKIE.equalsIgnoreCase(headerNameToCheck) ? "; " : " ";
          /**
           *  special attention needs to be given to make sure we maintain
           *  the attribute order else bad things can happen.
           *  1. String.split() always maintains the order in generated array
           *  2. LinkedHashSet is an ordered set
           *  3. *.stream().map() maintains the order *iff* the collection type is ordered
           *  4. *.collect() needs to be ordered as well to make sure the generated set is ordered
           */
          LinkedHashSet<String> headerValuesToCheck;
          if(headerToCheck.getName().equalsIgnoreCase(SET_COOKIE)) {
              /* make sure we maintain the order */
              headerValuesToCheck = new LinkedHashSet<>(Arrays.asList(headerToCheck.getValue().trim().split(";")));
              /* trim */
              headerValuesToCheck = headerValuesToCheck.stream().map(String::trim).collect(toCollection(LinkedHashSet::new));
          } else {
              headerValuesToCheck = new LinkedHashSet<>(Arrays.asList(headerToCheck.getValue().trim().split("\\s+")));
          }
          headerValuesToCheck = headerValuesToCheck.stream().map(h -> h.replaceAll(separator.trim(), "")).collect(toCollection(LinkedHashSet::new));
          headerValuesToCheck.removeIf(h -> excludedHeaderValues.stream().anyMatch(e -> h.contains(e)));
          return headerValuesToCheck.isEmpty() ? "" : String.join(separator, headerValuesToCheck);
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
