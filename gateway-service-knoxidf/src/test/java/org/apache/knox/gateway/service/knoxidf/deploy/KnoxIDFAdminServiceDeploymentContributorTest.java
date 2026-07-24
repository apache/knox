/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.service.knoxidf.deploy;

import org.apache.knox.gateway.deploy.DeploymentContext;
import org.apache.knox.gateway.deploy.ServiceDeploymentContributor;
import org.apache.knox.gateway.descriptor.FilterParamDescriptor;
import org.apache.knox.gateway.descriptor.GatewayDescriptor;
import org.apache.knox.gateway.descriptor.ResourceDescriptor;
import org.apache.knox.gateway.topology.Service;
import org.apache.knox.gateway.topology.Topology;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.ServiceLoader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Unit tests for {@link KnoxIDFAdminServiceDeploymentContributor}.
 *
 * Tests verify: (1) the Service SPI registration (ServiceLoader discovery);
 * (2) the values returned by the contributor's property methods (role, name,
 * packages, patterns); and (3) that {@code contributeService()} correctly wires
 * the gateway descriptor resource with the right role and pattern — verifies
 * that a deployed KNOXIDF_ADMIN service role produces a resource descriptor
 * with the correct role and URL pattern, as required for Knox gateway routing.
 */
public class KnoxIDFAdminServiceDeploymentContributorTest {

  @Test
  public void testRoleAndName() {
    final KnoxIDFAdminServiceDeploymentContributor c =
        new KnoxIDFAdminServiceDeploymentContributor();
    assertEquals("KNOXIDF_ADMIN", c.getRole());
    assertEquals("KNOXIDF_ADMIN", c.getName());
  }

  @Test
  public void testPackages() {
    final KnoxIDFAdminServiceDeploymentContributor c =
        new KnoxIDFAdminServiceDeploymentContributor();
    final String[] packages = c.getPackages();
    assertNotNull(packages);
    assertTrue("Expected org.apache.knox.gateway.service.knoxidf in packages",
        Arrays.asList(packages).contains("org.apache.knox.gateway.service.knoxidf"));
  }

  @Test
  public void testPatterns() {
    final KnoxIDFAdminServiceDeploymentContributor c =
        new KnoxIDFAdminServiceDeploymentContributor();
    final String[] patterns = c.getPatterns();
    assertNotNull(patterns);
    // Distinct from KnoxIDFServiceDeploymentContributor's "knoxidf/api/**?**" so that the
    // KNOXIDF role cannot accidentally serve admin endpoints, and so that per-role AclsAuthz
    // params (KNOXIDF_ADMIN.acl) apply only to trusted-issuer admin requests.
    assertTrue("Expected knoxidf/issuers-admin/**?** in patterns",
        Arrays.asList(patterns).contains("knoxidf/issuers-admin/**?**"));
  }

  @Test
  public void testServiceLoaderDiscovery() {
    for (ServiceDeploymentContributor c :
        ServiceLoader.load(ServiceDeploymentContributor.class)) {
      if (c instanceof KnoxIDFAdminServiceDeploymentContributor) {
        assertEquals("KNOXIDF_ADMIN", c.getRole());
        assertEquals("KNOXIDF_ADMIN", c.getName());
        return;
      }
    }
    fail("KnoxIDFAdminServiceDeploymentContributor not discoverable via ServiceLoader");
  }

  /**
   * Verifies that {@code contributeService()} sets the correct service role and URL pattern
   * on the gateway resource descriptor. This exercises the inherited
   * {@code JerseyServiceDeploymentContributorBase.contributeService()} with the concrete
   * values from {@code getPackages()} and {@code getPatterns()}.
   */
  @Test
  public void testContributeService() throws Exception {
    final KnoxIDFAdminServiceDeploymentContributor contributor =
        new KnoxIDFAdminServiceDeploymentContributor();

    // Mock FilterParamDescriptor for the jersey.config.server.provider.packages param.
    final FilterParamDescriptor param = EasyMock.createNiceMock(FilterParamDescriptor.class);
    EasyMock.expect(param.name(EasyMock.anyString())).andReturn(param).anyTimes();
    EasyMock.expect(param.value(EasyMock.anyString())).andReturn(param).anyTimes();
    EasyMock.replay(param);

    // Capture role and pattern set on the resource descriptor.
    final Capture<String> capturedRole = EasyMock.newCapture();
    final Capture<String> capturedPattern = EasyMock.newCapture();
    final ResourceDescriptor resource = EasyMock.createNiceMock(ResourceDescriptor.class);
    EasyMock.expect(resource.role(EasyMock.capture(capturedRole))).andReturn(resource).anyTimes();
    EasyMock.expect(resource.pattern(EasyMock.capture(capturedPattern)))
        .andReturn(resource).anyTimes();
    EasyMock.expect(resource.createFilterParam()).andReturn(param).anyTimes();
    EasyMock.expect(resource.filters()).andReturn(Collections.emptyList()).anyTimes();
    EasyMock.replay(resource);

    final GatewayDescriptor descriptor = EasyMock.createNiceMock(GatewayDescriptor.class);
    EasyMock.expect(descriptor.addResource()).andReturn(resource).anyTimes();
    EasyMock.replay(descriptor);

    // Use an empty topology so the base-class addXxxFilter calls are no-ops, isolating
    // the test to this contributor's specific contributions (role and pattern)"
    final Topology topology = new Topology();

    final DeploymentContext context = EasyMock.createNiceMock(DeploymentContext.class);
    EasyMock.expect(context.getGatewayDescriptor()).andReturn(descriptor).anyTimes();
    EasyMock.expect(context.getTopology()).andReturn(topology).anyTimes();
    EasyMock.replay(context);

    final Service service = new Service();
    service.setRole("KNOXIDF_ADMIN");
    service.setName("KNOXIDF_ADMIN");

    contributor.contributeService(context, service);

    assertEquals("KNOXIDF_ADMIN", capturedRole.getValue());
    assertEquals("knoxidf/issuers-admin/**?**", capturedPattern.getValue());
  }
}
