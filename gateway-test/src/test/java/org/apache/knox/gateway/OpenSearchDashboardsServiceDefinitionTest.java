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
package org.apache.knox.gateway;

import org.apache.knox.gateway.filter.rewrite.api.FrontendFunctionDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteEnvironment;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteProcessor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteRulesDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteRulesDescriptorFactory;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteServletContextListener;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteServletFilter;
import org.apache.knox.gateway.filter.rewrite.impl.UrlRewriteRequest;
import org.apache.knox.gateway.filter.rewrite.impl.UrlRewriteResponse;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.registry.ServiceRegistry;
import org.apache.knox.test.TestUtils;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.apache.knox.test.TestUtils.LOG_ENTER;
import static org.apache.knox.test.TestUtils.LOG_EXIT;
import static org.junit.Assert.assertEquals;

/**
 * Applies the inbound and outbound rules of the opensearch-dashboards service definition, which map the gateway
 * context to the backend base path (server.basePath=/opensearch-dashboards-ui, rewriteBasePath=false) and back.
 * Requests and responses are rewritten through {@link UrlRewriteRequest} and {@link UrlRewriteResponse} with the
 * filters the service.xml routes apply.
 */
public class OpenSearchDashboardsServiceDefinitionTest {

  private static final String RULES = "services/opensearch-dashboards/3.6.0/rewrite.xml";
  private static final String HEADERS_FILTER = "OPENSEARCH-DASHBOARDS/opensearch-dashboards/outbound/headers";
  private static final String BODY_FILTER = "OPENSEARCH-DASHBOARDS/opensearch-dashboards/outbound/body";
  private static final String FRONTEND_PATH = "/gateway/sandbox/opensearch-dashboards";

  private static final String HTML_INPUT =
      "<html><head>\n" +
      "<link rel=\"stylesheet\" href=\"/opensearch-dashboards-ui/ui/legacy_light_theme.css\">\n" +
      "<style>@font-face { src: url('/opensearch-dashboards-ui/ui/fonts/a.woff2') format('woff2'); }</style>\n" +
      "</head><body data=\"{&quot;basePath&quot;:&quot;/opensearch-dashboards-ui&quot;}\">\n" +
      "<a href=\"/opensearch-dashboards-ui/app/home\">home</a>\n" +
      "<a href=\"https://backend:5601/opensearch-dashboards-ui/app/home\">backend</a>\n" +
      "<a href=\"/other/opensearch-dashboards-ui\">other</a>\n" +
      "<a href=\"/opensearch-dashboards-ui-v2\">v2</a>\n" +
      "</body></html>";
  private static final String HTML_EXPECTED =
      "<html><head>\n" +
      "<link rel=\"stylesheet\" href=\"" + FRONTEND_PATH + "/ui/legacy_light_theme.css\">\n" +
      "<style>@font-face { src: url('" + FRONTEND_PATH + "/ui/fonts/a.woff2') format('woff2'); }</style>\n" +
      "</head><body data=\"{&quot;basePath&quot;:&quot;" + FRONTEND_PATH + "&quot;}\">\n" +
      "<a href=\"" + FRONTEND_PATH + "/app/home\">home</a>\n" +
      "<a href=\"https://backend:5601/opensearch-dashboards-ui/app/home\">backend</a>\n" +
      "<a href=\"/other/opensearch-dashboards-ui\">other</a>\n" +
      "<a href=\"/opensearch-dashboards-ui-v2\">v2</a>\n" +
      "</body></html>";

  private FilterConfig filterConfig;

  @Before
  public void setUp() throws Exception {
    // Mock out the service url registry which is required for several url rewrite functions to work.
    ServiceRegistry registry = EasyMock.createNiceMock( ServiceRegistry.class );
    EasyMock.expect( registry.lookupServiceURL( "sandbox", "OPENSEARCH-DASHBOARDS" ) ).andReturn( "https://backend:5601" ).anyTimes();

    GatewayServices services = EasyMock.createNiceMock( GatewayServices.class );
    EasyMock.expect( services.getService( ServiceType.SERVICE_REGISTRY_SERVICE ) ).andReturn( registry ).anyTimes();

    UrlRewriteEnvironment environment = EasyMock.createNiceMock( UrlRewriteEnvironment.class );
    EasyMock.expect( environment.getAttribute( FrontendFunctionDescriptor.FRONTEND_URI_ATTRIBUTE ) ).andReturn( new URI( "https://knox-host:8443/gateway/sandbox" ) ).anyTimes();
    EasyMock.expect( environment.getAttribute( GatewayServices.GATEWAY_SERVICES_ATTRIBUTE ) ).andReturn( services ).anyTimes();
    EasyMock.expect( environment.getAttribute( GatewayServices.GATEWAY_CLUSTER_ATTRIBUTE ) ).andReturn( "sandbox" ).anyTimes();

    UrlRewriteRulesDescriptor rewriteRules;
    try( Reader rulesReader = TestUtils.getResourceReader( RULES, StandardCharsets.UTF_8 ) ) {
      rewriteRules = UrlRewriteRulesDescriptorFactory.load( "xml", rulesReader );
    }
    UrlRewriteProcessor rewriteProcessor = new UrlRewriteProcessor();

    ServletContext servletContext = EasyMock.createNiceMock( ServletContext.class );
    EasyMock.expect( servletContext.getAttribute( UrlRewriteServletContextListener.PROCESSOR_ATTRIBUTE_NAME ) ).andReturn( rewriteProcessor ).anyTimes();

    // The filters the service.xml routes apply to the response.
    filterConfig = EasyMock.createNiceMock( FilterConfig.class );
    EasyMock.expect( filterConfig.getServletContext() ).andReturn( servletContext ).anyTimes();
    EasyMock.expect( filterConfig.getInitParameter( UrlRewriteServletFilter.RESPONSE_HEADERS_FILTER_PARAM ) ).andReturn( HEADERS_FILTER ).anyTimes();
    EasyMock.expect( filterConfig.getInitParameter( UrlRewriteServletFilter.RESPONSE_BODY_FILTER_PARAM ) ).andReturn( BODY_FILTER ).anyTimes();

    EasyMock.replay( registry, services, environment, servletContext, filterConfig );

    rewriteProcessor.initialize( environment, rewriteRules );
  }

  @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
  public void testInboundRewrite() throws Exception {
    LOG_ENTER();
    assertEquals( "https://backend:5601/", rewriteRequestUrl( "/gateway/sandbox/opensearch-dashboards", null ) );
    assertEquals( "https://backend:5601/", rewriteRequestUrl( "/gateway/sandbox/opensearch-dashboards/", null ) );
    assertEquals( "https://backend:5601/app/home", rewriteRequestUrl( "/gateway/sandbox/opensearch-dashboards/app/home", null ) );
    assertEquals( "https://backend:5601/app/home?x=1", rewriteRequestUrl( "/gateway/sandbox/opensearch-dashboards/app/home", "x=1" ) );
    // The service context name also appears in OSD's own API paths. The rules anchor the context after the
    // gateway path and topology segments, a /**/ prefix would bind to the last occurrence instead.
    assertEquals( "https://backend:5601/api/opensearch-dashboards/management/saved_objects/_allowed_types",
        rewriteRequestUrl( "/gateway/sandbox/opensearch-dashboards/api/opensearch-dashboards/management/saved_objects/_allowed_types", null ) );
    assertEquals( "https://backend:5601/api/opensearch-dashboards/settings?x=1",
        rewriteRequestUrl( "/gateway/sandbox/opensearch-dashboards/api/opensearch-dashboards/settings", "x=1" ) );
    LOG_EXIT();
  }

  @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
  public void testLocationRewrite() throws Exception {
    LOG_ENTER();
    assertEquals( FRONTEND_PATH + "/app/home", rewriteLocation( "/opensearch-dashboards-ui/app/home" ) );
    assertEquals( FRONTEND_PATH + "/app/home?x=1", rewriteLocation( "/opensearch-dashboards-ui/app/home?x=1" ) );
    assertEquals( FRONTEND_PATH, rewriteLocation( "/opensearch-dashboards-ui" ) );
    LOG_EXIT();
  }

  @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
  public void testHtmlBodyRewrite() throws Exception {
    LOG_ENTER();
    assertEquals( HTML_EXPECTED, new String( rewriteBody( "text/html", HTML_INPUT.getBytes( StandardCharsets.UTF_8 ) ), StandardCharsets.UTF_8 ) );
    LOG_EXIT();
  }

  @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
  public void testGzipHtmlBodyRewrite() throws Exception {
    LOG_ENTER();
    // service.xml requests gzip from the backend so that the body can be decoded for rewriting.
    byte[] output = rewriteBody( "text/html", gzip( HTML_INPUT.getBytes( StandardCharsets.UTF_8 ) ) );
    assertEquals( HTML_EXPECTED, new String( gunzip( output ), StandardCharsets.UTF_8 ) );
    LOG_EXIT();
  }

  @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
  public void testJavaScriptBodyRewrite() throws Exception {
    LOG_ENTER();
    String input =
        "__osdBundles__.define('/opensearch-dashboards-ui/bundles/core.entry.js');\n" +
        "var basePath = \"/opensearch-dashboards-ui\";\n" +
        "var backend = 'https://backend:5601/opensearch-dashboards-ui/app/home';\n" +
        "var other = '/other/opensearch-dashboards-ui', v2 = '/opensearch-dashboards-ui-v2';\n";
    String expected =
        "__osdBundles__.define('" + FRONTEND_PATH + "/bundles/core.entry.js');\n" +
        "var basePath = \"" + FRONTEND_PATH + "\";\n" +
        "var backend = 'https://backend:5601/opensearch-dashboards-ui/app/home';\n" +
        "var other = '/other/opensearch-dashboards-ui', v2 = '/opensearch-dashboards-ui-v2';\n";
    assertEquals( expected, new String( rewriteBody( "application/javascript", input.getBytes( StandardCharsets.UTF_8 ) ), StandardCharsets.UTF_8 ) );
    LOG_EXIT();
  }

  private String rewriteRequestUrl( String path, String query ) {
    HttpServletRequest request = EasyMock.createNiceMock( HttpServletRequest.class );
    EasyMock.expect( request.getScheme() ).andReturn( "https" ).anyTimes();
    EasyMock.expect( request.getServerName() ).andReturn( "knox-host" ).anyTimes();
    EasyMock.expect( request.getServerPort() ).andReturn( 8443 ).anyTimes();
    EasyMock.expect( request.getRequestURI() ).andReturn( path ).anyTimes();
    EasyMock.expect( request.getQueryString() ).andReturn( query ).anyTimes();
    EasyMock.replay( request );

    UrlRewriteRequest rewriteRequest = new UrlRewriteRequest( filterConfig, request );
    String targetQuery = rewriteRequest.getQueryString();
    return rewriteRequest.getRequestURI() + ( targetQuery == null ? "" : "?" + targetQuery );
  }

  private String rewriteLocation( String location ) {
    Capture<String> header = Capture.newInstance();
    HttpServletResponse response = EasyMock.createNiceMock( HttpServletResponse.class );
    response.setHeader( EasyMock.eq( "Location" ), EasyMock.capture( header ) );
    EasyMock.replay( response );

    newRewriteResponse( response ).setHeader( "Location", location );
    return header.getValue();
  }

  private byte[] rewriteBody( String contentType, byte[] input ) throws Exception {
    HttpServletResponse response = EasyMock.createNiceMock( HttpServletResponse.class );
    EasyMock.expect( response.getContentType() ).andReturn( contentType ).anyTimes();
    EasyMock.replay( response );

    ByteArrayOutputStream output = new ByteArrayOutputStream();
    newRewriteResponse( response ).streamResponse( new ByteArrayInputStream( input ), output );
    return output.toByteArray();
  }

  private UrlRewriteResponse newRewriteResponse( HttpServletResponse response ) {
    HttpServletRequest request = EasyMock.createNiceMock( HttpServletRequest.class );
    EasyMock.replay( request );
    return new UrlRewriteResponse( filterConfig, request, response );
  }

  private static byte[] gzip( byte[] input ) throws Exception {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    try( OutputStream stream = new GZIPOutputStream( output ) ) {
      stream.write( input );
    }
    return output.toByteArray();
  }

  private static byte[] gunzip( byte[] input ) throws Exception {
    try( InputStream stream = new GZIPInputStream( new ByteArrayInputStream( input ) ) ) {
      return stream.readAllBytes();
    }
  }
}
