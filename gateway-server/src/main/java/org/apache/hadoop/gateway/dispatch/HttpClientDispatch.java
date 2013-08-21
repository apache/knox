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
package org.apache.hadoop.gateway.dispatch;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.Principal;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.hadoop.gateway.GatewayMessages;
import org.apache.hadoop.gateway.GatewayResources;
import org.apache.hadoop.gateway.config.GatewayConfig;
import org.apache.hadoop.gateway.i18n.messages.MessagesFactory;
import org.apache.hadoop.gateway.i18n.resources.ResourcesFactory;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.auth.Credentials;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicHeader;

/**
 *
 */
public class HttpClientDispatch extends AbstractGatewayDispatch {
  
  // private static final String CT_APP_WWW_FORM_URL_ENCODED = "application/x-www-form-urlencoded";
  // private static final String CT_APP_XML = "application/xml";
  private static final String Q_DELEGATION_EQ = "?delegation=";
  private static final String AMP_DELEGATION_EQ = "&delegation=";
  private static final String COOKIE = "Cookie";
  private static final String SET_COOKIE = "Set-Cookie";
  private static final String WWW_AUTHENTICATE = "WWW-Authenticate";
  private static final String NEGOTIATE = "Negotiate";

  private static GatewayMessages LOG = MessagesFactory.get( GatewayMessages.class );
  private static GatewayResources RES = ResourcesFactory.get( GatewayResources.class );
  private static final int REPLAY_BUFFER_MAX_SIZE = 1024 * 1024; // limit to 1MB

  private AppCookieManager appCookieManager = new AppCookieManager();;
  
  protected void executeRequest(
      HttpUriRequest outboundRequest,
      HttpServletRequest inboundRequest,
      HttpServletResponse outboundResponse )
          throws IOException {
    LOG.dispatchRequest( outboundRequest.getMethod(), outboundRequest.getURI() );
    DefaultHttpClient client = new DefaultHttpClient();

    HttpResponse inboundResponse;
    try {
      String query = outboundRequest.getURI().getQuery();
      if (!"true".equals(System.getProperty(GatewayConfig.HADOOP_KERBEROS_SECURED))) {
        // Hadoop cluster not Kerberos enabled
        inboundResponse = client.execute(outboundRequest);
      } else if (query.contains(Q_DELEGATION_EQ) ||
        // query string carries delegation token
        query.contains(AMP_DELEGATION_EQ)) {
        inboundResponse = client.execute(outboundRequest);
      } else { 
        // Kerberos secured, no delegation token in query string
        outboundRequest.removeHeaders(COOKIE);
        String appCookie = appCookieManager.getCachedAppCookie();
        if (appCookie != null) {
          outboundRequest.addHeader(new BasicHeader(COOKIE, appCookie));
        }
        inboundResponse = client.execute(outboundRequest);
        // if inBoundResponse has status 401 and header WWW-Authenticate: Negoitate
        // refresh hadoop.auth.cookie and attempt one more time
        int statusCode = inboundResponse.getStatusLine().getStatusCode();
        if (statusCode == HttpStatus.SC_UNAUTHORIZED ) {
          Header[] wwwAuthHeaders = inboundResponse.getHeaders(WWW_AUTHENTICATE) ;
          if (wwwAuthHeaders != null && wwwAuthHeaders.length != 0 && 
              wwwAuthHeaders[0].getValue().trim().startsWith(NEGOTIATE)) {
            appCookie = appCookieManager.getAppCookie(outboundRequest, true);
            outboundRequest.removeHeaders(COOKIE);
            outboundRequest.addHeader(new BasicHeader(COOKIE, appCookie));
            client = new DefaultHttpClient();
            inboundResponse = client.execute(outboundRequest);
          } else {
            // no supported authentication type found
            // we would let the original response propogate
          }
        } else {
          // not a 401 Unauthorized status code
          // we would let the original response propogate
        }
      }
    } catch (IOException e) {
      // we do not want to expose back end host. port end points to clients, see JIRA KNOX-58
      LOG.dispatchServiceConnectionException( outboundRequest.getURI(), e );
      throw new IOException( RES.dispatchConnectionError() );
    }

    // Copy the client respond header to the server respond.
    outboundResponse.setStatus( inboundResponse.getStatusLine().getStatusCode() );
    Header[] headers = inboundResponse.getAllHeaders();
    for( Header header : headers ) {
      String name = header.getName();
      if (name.equals(SET_COOKIE) || name.equals(WWW_AUTHENTICATE)) {
        continue;
      }
      String value = header.getValue();
      outboundResponse.addHeader( name, value );
    }

    HttpEntity entity = inboundResponse.getEntity();
    if( entity != null ) {
      Header contentType = entity.getContentType();
      if( contentType != null ) {
        outboundResponse.setContentType( contentType.getValue() );
      }
//KM[ If this is set here it ends up setting the content length to the content returned from the server.
// This length might not match if the the content is rewritten.
//      long contentLength = entity.getContentLength();
//      if( contentLength <= Integer.MAX_VALUE ) {
//        outboundResponse.setContentLength( (int)contentLength );
//      }
//]
      writeResponse( inboundRequest, outboundResponse, entity.getContent() );
    }
  }

  protected HttpEntity createRequestEntity(HttpServletRequest request)
      throws IOException {

    String contentType = request.getContentType();
//    String contentEncoding = request.getCharacterEncoding();
    int contentLength = request.getContentLength();
    InputStream contentStream = request.getInputStream();

    HttpEntity entity;
    if( contentType == null ) {
      entity = new InputStreamEntity( contentStream, contentLength );
    } else {
      entity = new InputStreamEntity(contentStream, contentLength, ContentType.parse( contentType ) );
    }
    //TODO: Need a better solution than this for replaying bodies when required.
    // Perhaps we should pessimistically buffer the first REPLAY_BUFFER_MAX_SIZE bytes and then
    // switch to the remaining stream once that is consumed.  The buffer size would probably need
    // to match the underlying socket buffer size for this to be meaningful.
    // This would require writing a special version of HttpEntity/BufferedHttpEntity.
    // Might also look into mark()/reset() as a solution.
    if( contentLength <= REPLAY_BUFFER_MAX_SIZE ) {
      entity = new BufferedHttpEntity( entity );
    }

//    HttpEntity entity = null;
//    if ((contentType != null)
//        && (contentType.startsWith(CT_APP_WWW_FORM_URL_ENCODED) ||
//            contentType.equalsIgnoreCase(CT_APP_XML))) {
//      if (contentLength <= REPLAY_BUFFER_MAX_SIZE) {
//        if (contentEncoding == null) {
//          contentEncoding = Charset.defaultCharset().name();
//        }
//        String body = IOUtils.toString(contentStream, contentEncoding);
//        // ASCII is OK here because the urlEncode about should have already
//        // escaped
//        byte[] bodyBytes = body.getBytes("US-ASCII");
//        ContentType ct = contentType.equalsIgnoreCase(CT_APP_XML) ? ContentType.APPLICATION_XML
//            : ContentType.APPLICATION_FORM_URLENCODED;
//        entity = new ByteArrayEntity(bodyBytes, ct);
//      } else {
//        entity = new InputStreamEntity(contentStream, contentLength);
//      }
//    } else {
//      InputStreamEntity streamEntity = new RepeatableInputStreamEntity(
//          contentStream, contentLength);
//      if (contentType != null) {
//        streamEntity.setContentType(contentType);
//      }
//      if (contentEncoding != null) {
//        streamEntity.setContentEncoding(contentEncoding);
//      }
//      entity = streamEntity;
//    }
    return entity;
  }

  @Override
  public void doGet( URI url, HttpServletRequest request, HttpServletResponse response )
      throws IOException, URISyntaxException {
    HttpGet method = new HttpGet( url );
    copyRequestHeaderFields( method, request );
    executeRequest( method, request, response );
  }

  @Override
  public void doOptions( URI url, HttpServletRequest request, HttpServletResponse response )
      throws IOException, URISyntaxException {
    HttpOptions method = new HttpOptions( url );
    executeRequest( method, request, response );
  }

  @Override
  public void doPut( URI url, HttpServletRequest request, HttpServletResponse response )
      throws IOException, URISyntaxException {
    HttpPut method = new HttpPut( url );
    HttpEntity entity = createRequestEntity( request );
    method.setEntity( entity );
    copyRequestHeaderFields( method, request );
    executeRequest( method, request, response );
  }

  @Override
  public void doPost( URI url, HttpServletRequest request, HttpServletResponse response )
      throws IOException, URISyntaxException {
    HttpPost method = new HttpPost( url );
    HttpEntity entity = createRequestEntity( request );
    method.setEntity( entity );
    copyRequestHeaderFields( method, request );
    executeRequest( method, request, response );
  }

  @Override
  public void doDelete( URI url, HttpServletRequest request, HttpServletResponse response )
      throws IOException, URISyntaxException {
    HttpDelete method = new HttpDelete( url );
    copyRequestHeaderFields( method, request );
    executeRequest( method, request, response );
  }
  
//  private static class RepeatableInputStreamEntity extends InputStreamEntity {
//
//    public RepeatableInputStreamEntity(InputStream contentStream,
//        int contentLength) {
//      super(contentStream, contentLength);
//    }
//
//    @Override
//    public boolean isRepeatable() {
//      return true;
//    }
//
//    @Override
//    public InputStream getContent() throws IOException {
//      return super.getContent();
//    }
//
//  }

}
