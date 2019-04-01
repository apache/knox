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

import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteEnvironment;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteProcessor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteRulesDescriptor;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteRulesDescriptorFactory;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteServletContextListener;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteServletEnvironment;
import org.apache.knox.gateway.filter.rewrite.api.UrlRewriteServletFilter;
import org.apache.knox.gateway.filter.rewrite.impl.UrlRewriteRequest;
import org.apache.knox.gateway.services.ServiceType;
import org.apache.knox.gateway.services.GatewayServices;
import org.apache.knox.gateway.services.registry.ServiceRegistry;
import org.apache.knox.gateway.util.XmlUtils;
import org.apache.knox.test.TestUtils;
import org.apache.knox.test.mock.MockServletInputStream;
import org.easymock.EasyMock;
import org.junit.Test;
import org.w3c.dom.Document;

import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import static org.apache.knox.test.TestUtils.LOG_ENTER;
import static org.apache.knox.test.TestUtils.LOG_EXIT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.xml.HasXPath.hasXPath;

public class OozieServiceDefinitionTest {

  @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
  public void testOozieRewriteRulesForLiteralTemplateValuesBugKnox394() throws Exception {
    LOG_ENTER();

    // This is a unique part of this test.
    String testResource = "oozie-request-with-var.xml";

    // Mock out the service url registry which is required for several url rewrite functions to work.
    ServiceRegistry registry = EasyMock.createNiceMock( ServiceRegistry.class );
    EasyMock.expect( registry.lookupServiceURL( "test-cluster", "NAMENODE" ) ).andReturn( "test-scheme://test-host:42" ).anyTimes();

    // Mock out the gateway services registry which is required for several url rewrite functions to work.
    GatewayServices services = EasyMock.createNiceMock( GatewayServices.class );
    EasyMock.expect( services.getService( ServiceType.SERVICE_REGISTRY_SERVICE ) ).andReturn( registry ).anyTimes();

    UrlRewriteProcessor rewriteProcessor = new UrlRewriteProcessor();

    ServletContext servletContext = EasyMock.createNiceMock( ServletContext.class );
    EasyMock.expect( servletContext.getAttribute( UrlRewriteServletContextListener.PROCESSOR_ATTRIBUTE_NAME ) ).andReturn( rewriteProcessor ).anyTimes();
    EasyMock.expect( servletContext.getAttribute( GatewayServices.GATEWAY_SERVICES_ATTRIBUTE ) ).andReturn( services ).anyTimes();
    EasyMock.expect( servletContext.getAttribute( GatewayServices.GATEWAY_CLUSTER_ATTRIBUTE ) ).andReturn( "test-cluster" ).anyTimes();

    HttpServletRequest servletRequest = EasyMock.createNiceMock( HttpServletRequest.class );
    EasyMock.expect( servletRequest.getInputStream() ).andReturn( new MockServletInputStream( TestUtils.getResourceStream( OozieServiceDefinitionTest.class, testResource ) ) ).anyTimes();
    EasyMock.expect( servletRequest.getContentType() ).andReturn( "text/xml" ).anyTimes();

    FilterConfig filterConfig = EasyMock.createNiceMock( FilterConfig.class );
    EasyMock.expect( filterConfig.getServletContext() ).andReturn( servletContext ).anyTimes();
    EasyMock.expect( filterConfig.getInitParameter( UrlRewriteServletFilter.REQUEST_BODY_FILTER_PARAM ) ).andReturn( "OOZIE/oozie/configuration" ).anyTimes();

    EasyMock.replay( registry, services, servletContext, servletRequest, filterConfig );

    UrlRewriteEnvironment rewriteEnvironment = new UrlRewriteServletEnvironment( servletContext );

    Reader rulesReader = TestUtils.getResourceReader( "services/oozie/4.0.0/rewrite.xml", StandardCharsets.UTF_8 );
    UrlRewriteRulesDescriptor rewriteRules = UrlRewriteRulesDescriptorFactory
        .load( "xml", rulesReader );
    rulesReader.close();

    rewriteProcessor.initialize( rewriteEnvironment, rewriteRules );

    UrlRewriteRequest rewriteRequest = new UrlRewriteRequest( filterConfig, servletRequest );

    InputStream stream = rewriteRequest.getInputStream();

    Document document = XmlUtils.readXml( stream );

    assertThat( document,
        hasXPath( "/configuration/property[name='oozie.wf.application.path']/value",
            equalTo( "${appPath}/workflow.xml" ) ) );

    LOG_EXIT();
  }

  @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
  public void testOozieRewriteRulesForLiteralComplexTemplateValuesBugKnox394() throws Exception {
    LOG_ENTER();

    // This is a unique part of this test.
    String testResource = "oozie-request-with-complex-var.xml";

    // Mock out the service url registry which is required for several url rewrite functions to work.
    ServiceRegistry registry = EasyMock.createNiceMock( ServiceRegistry.class );
    EasyMock.expect( registry.lookupServiceURL( "test-cluster", "NAMENODE" ) ).andReturn( "test-scheme://test-host:42" ).anyTimes();

    // Mock out the gateway services registry which is required for several url rewrite functions to work.
    GatewayServices services = EasyMock.createNiceMock( GatewayServices.class );
    EasyMock.expect( services.getService( ServiceType.SERVICE_REGISTRY_SERVICE ) ).andReturn( registry ).anyTimes();

    UrlRewriteProcessor rewriteProcessor = new UrlRewriteProcessor();

    ServletContext servletContext = EasyMock.createNiceMock( ServletContext.class );
    EasyMock.expect( servletContext.getAttribute( UrlRewriteServletContextListener.PROCESSOR_ATTRIBUTE_NAME ) ).andReturn( rewriteProcessor ).anyTimes();
    EasyMock.expect( servletContext.getAttribute( GatewayServices.GATEWAY_SERVICES_ATTRIBUTE ) ).andReturn( services ).anyTimes();
    EasyMock.expect( servletContext.getAttribute( GatewayServices.GATEWAY_CLUSTER_ATTRIBUTE ) ).andReturn( "test-cluster" ).anyTimes();

    HttpServletRequest servletRequest = EasyMock.createNiceMock( HttpServletRequest.class );
    EasyMock.expect( servletRequest.getInputStream() ).andReturn( new MockServletInputStream( TestUtils.getResourceStream( OozieServiceDefinitionTest.class, testResource ) ) ).anyTimes();
    EasyMock.expect( servletRequest.getContentType() ).andReturn( "text/xml" ).anyTimes();

    FilterConfig filterConfig = EasyMock.createNiceMock( FilterConfig.class );
    EasyMock.expect( filterConfig.getServletContext() ).andReturn( servletContext ).anyTimes();
    EasyMock.expect( filterConfig.getInitParameter( UrlRewriteServletFilter.REQUEST_BODY_FILTER_PARAM ) ).andReturn( "OOZIE/oozie/configuration" ).anyTimes();

    EasyMock.replay( registry, services, servletContext, servletRequest, filterConfig );

    UrlRewriteEnvironment rewriteEnvironment = new UrlRewriteServletEnvironment( servletContext );

    Reader rulesReader = TestUtils.getResourceReader( "services/oozie/4.0.0/rewrite.xml", StandardCharsets.UTF_8 );
    UrlRewriteRulesDescriptor rewriteRules = UrlRewriteRulesDescriptorFactory.load( "xml", rulesReader );
    rulesReader.close();

    rewriteProcessor.initialize( rewriteEnvironment, rewriteRules );

    UrlRewriteRequest rewriteRequest = new UrlRewriteRequest( filterConfig, servletRequest );

    InputStream stream = rewriteRequest.getInputStream();

    Document document = XmlUtils.readXml( stream );

    assertThat( document,
        hasXPath( "/configuration/property[name='oozie.wf.application.path']/value",
            equalTo( "${nameNode}/user/${user.name}/${examplesRoot}/apps/hive" ) ) );

    LOG_EXIT();
  }

  @Test( timeout = TestUtils.MEDIUM_TIMEOUT )
  public void testOozieRewriteRulesForValuesRelativeToServiceRegistry() throws Exception {
    LOG_ENTER();

    // This is a unique part of this test.
    String testResource = "oozie-request-relative.xml";

    // Mock out the service url registry which is required for several url rewrite functions to work.
    ServiceRegistry registry = EasyMock.createNiceMock( ServiceRegistry.class );
    EasyMock.expect( registry.lookupServiceURL( "test-cluster", "NAMENODE" ) ).andReturn( "test-scheme://test-host:42" ).anyTimes();

    // Mock out the gateway services registry which is required for several url rewrite functions to work.
    GatewayServices services = EasyMock.createNiceMock( GatewayServices.class );
    EasyMock.expect( services.getService( ServiceType.SERVICE_REGISTRY_SERVICE ) ).andReturn( registry ).anyTimes();

    UrlRewriteProcessor rewriteProcessor = new UrlRewriteProcessor();

    ServletContext servletContext = EasyMock.createNiceMock( ServletContext.class );
    EasyMock.expect( servletContext.getAttribute( UrlRewriteServletContextListener.PROCESSOR_ATTRIBUTE_NAME ) ).andReturn( rewriteProcessor ).anyTimes();
    EasyMock.expect( servletContext.getAttribute( GatewayServices.GATEWAY_SERVICES_ATTRIBUTE ) ).andReturn( services ).anyTimes();
    EasyMock.expect( servletContext.getAttribute( GatewayServices.GATEWAY_CLUSTER_ATTRIBUTE ) ).andReturn( "test-cluster" ).anyTimes();

    HttpServletRequest servletRequest = EasyMock.createNiceMock( HttpServletRequest.class );
    EasyMock.expect( servletRequest.getInputStream() ).andReturn( new MockServletInputStream( TestUtils.getResourceStream( OozieServiceDefinitionTest.class, testResource ) ) ).anyTimes();
    EasyMock.expect( servletRequest.getContentType() ).andReturn( "text/xml" ).anyTimes();
    EasyMock.expect( servletRequest.getContentLength() ).andReturn( -1 ).anyTimes();

    FilterConfig filterConfig = EasyMock.createNiceMock( FilterConfig.class );
    EasyMock.expect( filterConfig.getServletContext() ).andReturn( servletContext ).anyTimes();
    EasyMock.expect( filterConfig.getInitParameter( UrlRewriteServletFilter.REQUEST_BODY_FILTER_PARAM ) ).andReturn( "OOZIE/oozie/configuration" ).anyTimes();

    EasyMock.replay( registry, services, servletContext, servletRequest, filterConfig );

    UrlRewriteEnvironment rewriteEnvironment = new UrlRewriteServletEnvironment( servletContext );

    Reader rulesReader = TestUtils.getResourceReader( "services/oozie/4.0.0/rewrite.xml", StandardCharsets.UTF_8 );
    UrlRewriteRulesDescriptor rewriteRules = UrlRewriteRulesDescriptorFactory.load( "xml", rulesReader );
    rulesReader.close();

    rewriteProcessor.initialize( rewriteEnvironment, rewriteRules );

    UrlRewriteRequest rewriteRequest = new UrlRewriteRequest( filterConfig, servletRequest );

    InputStream stream = rewriteRequest.getInputStream();

    Document document = XmlUtils.readXml( stream );

    assertThat( document,
        hasXPath( "/configuration/property[name='oozie.wf.application.path']/value",
            equalTo( "test-scheme://test-host:42/workflow.xml" ) ) );

    LOG_EXIT();
  }

}
