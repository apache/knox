/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.knox.gateway.topology;

import org.junit.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class TopologyTest {


  @Test
  public void testIdenticalTopologyObjectsAreEqual() {
    Topology t1 = new Topology();
    Topology t2 = t1;
    assertEquals(t1, t2);
  }

  @Test
  public void testNullProviders() {
    Topology t1 = new Topology();
    Topology t2 = new Topology();

    t1.providerList = null;
    assertNotEquals(t1, t2);

    t1.providerList = Collections.emptyList();
    t2.providerList = null;
    assertNotEquals(t1, t2);

    t1.providerList = null;
    assertEquals(t1, t2);
  }

  @Test
  public void testNullServices() {
    Topology t1 = new Topology();
    Topology t2 = new Topology();

    t1.services = null;
    assertNotEquals(t1, t2);

    t1.services = Collections.emptyList();
    t2.services = null;
    assertNotEquals(t1, t2);

    t1.services = null;
    assertEquals(t1, t2);
  }

  @Test
  public void testNullApplications() {
    Topology t1 = new Topology();
    Topology t2 = new Topology();

    t1.applications = null;
    assertNotEquals(t1, t2);

    t1.applications = Collections.emptyList();
    t2.applications = null;
    assertNotEquals(t1, t2);

    t1.applications = null;
    assertEquals(t1, t2);
  }

  @Test
  public void testEmptyTopologiesWithSameName() {
    final String name = "tName";
    Topology t1 = createTopology(name, Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    Topology t2 = createTopology(name, Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    assertEquals(t1, t2);
    assertEquals("hashcode must be equal if objects are equal.", t1.hashCode(), t2.hashCode());
  }

  @Test
  public void testEmptyTopologiesWithDifferentName() {
    Topology t1 = createTopology("tName1", Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    Topology t2 = createTopology("tName2", Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    assertNotEquals(t1, t2);
    assertNotEquals("hashcode must be equal if objects are equal.", t1.hashCode(), t2.hashCode());
  }

  @Test
  public void testTopologiesWithSameServicesInDifferentOrder() {
    final String name = "topologyX";

    final String serviceName1 = "service1";
    final String serviceRole1 = "role1";
    Service service1 = createService(serviceName1, serviceRole1, Collections.emptyList(), Collections.emptyMap());

    final String serviceName2 = "service2";
    final String serviceRole2 = "role2";
    Service service2 = createService(serviceName2, serviceRole2, Collections.emptyList(), Collections.emptyMap());

    final String serviceName3 = "service3";
    final String serviceRole3 = "role3";
    Service service3 = createService(serviceName3, serviceRole3, Collections.emptyList(), Collections.emptyMap());

    List<Service> services1 = Arrays.asList(service1, service2, service3);
    List<Service> services2 = Arrays.asList(service2, service3, service1);

    Topology t1 = createTopology(name, Collections.emptyList(), services1, Collections.emptyList());
    Topology t2 = createTopology(name, Collections.emptyList(), services2, Collections.emptyList());

    assertEquals(t1, t2);
    assertEquals("hashcode must be equal if objects are equal.", t1.hashCode(), t2.hashCode());
  }

  @Test
  public void testTopologiesWithSameServicesWithNullURLs() {
    final String name = "topologyX";

    final String serviceName1 = "service1";
    final String serviceRole1 = "role1";
    Service service1 = createService(serviceName1,
                                     serviceRole1,
                                     null,                    // Null URLs
                                     Collections.emptyMap());

    List<Service> services1 = Collections.singletonList(service1);
    List<Service> services2 = Collections.singletonList(service1);

    Topology t1 = createTopology(name, Collections.emptyList(), services1, Collections.emptyList());
    Topology t2 = createTopology(name, Collections.emptyList(), services2, Collections.emptyList());

    assertEquals(t1, t2);
    assertEquals("hashcode must be equal if objects are equal.", t1.hashCode(), t2.hashCode());
  }

  @Test
  public void testTopologiesWithSameServicesWithDifferentURLOrder() {
    final String name = "topologyX";

    final String url1 = "http://host:1234/path1";
    final String url2 = "http://host:1234/path2";
    final String url3 = "http://host:1234/path3";

    final String serviceName1 = "service1";
    final String serviceRole1 = "role1";
    Service service1 =
                  createService(serviceName1, serviceRole1, Arrays.asList(url1, url2, url3), Collections.emptyMap());

    final String serviceName2 = "service2";
    final String serviceRole2 = "role2";
    Service service2 =
                  createService(serviceName2, serviceRole2, Arrays.asList(url2, url3, url1), Collections.emptyMap());

    final String serviceName3 = "service3";
    final String serviceRole3 = "role3";
    Service service3 =
                  createService(serviceName3, serviceRole3, Arrays.asList(url3, url2, url1), Collections.emptyMap());

    List<Service> services1 = Arrays.asList(service1, service2, service3);
    List<Service> services2 = Arrays.asList(service2, service3, service1);

    Topology t1 = createTopology(name, Collections.emptyList(), services1, Collections.emptyList());
    Topology t2 = createTopology(name, Collections.emptyList(), services2, Collections.emptyList());

    assertEquals(t1, t2);
    assertEquals("hashcode must be equal if objects are equal.", t1.hashCode(), t2.hashCode());
  }

  @Test
  public void testTopologiesWithSameProvidersInDifferentOrder() {
    final String name = "topologyX";

    final String name1 = "provider1";
    final String role1 = "role1";
    Provider provider1 = createProvider(name1, role1, Collections.emptyMap());

    final String name2 = "provider1";
    final String role2 = "role1";
    Provider provider2 = createProvider(name2, role2, Collections.emptyMap());

    final String name3 = "provider1";
    final String role3 = "role1";
    Provider provider3 = createProvider(name3, role3, Collections.emptyMap());

    List<Provider> providers1 = Arrays.asList(provider1, provider2, provider3);
    List<Provider> providers2 = Arrays.asList(provider3, provider2, provider1);

    Topology t1 = createTopology(name, providers1, Collections.emptyList(), Collections.emptyList());
    Topology t2 = createTopology(name, providers2, Collections.emptyList(), Collections.emptyList());

    assertEquals(t1, t2);
    assertEquals("hashcode must be equal if objects are equal.", t1.hashCode(), t2.hashCode());
  }


  @Test
  public void testTopologiesWithSameAppsInDifferentOrder() {
    final String name = "topologyX";

    final String name1 = "app1";
    final String role1 = "role1";
    Application app1 = createApplication(name1, role1, Collections.emptyList(), Collections.emptyMap());

    final String name2 = "app2";
    final String role2 = "role2";
    Application app2 = createApplication(name2, role2, Collections.emptyList(), Collections.emptyMap());

    final String name3 = "app3";
    final String role3 = "role3";
    Application app3 = createApplication(name3, role3, Collections.emptyList(), Collections.emptyMap());

    List<Application> apps1 = Arrays.asList(app1, app2, app3);
    List<Application> apps2 = Arrays.asList(app3, app1, app2);

    Topology t1 = createTopology(name, Collections.emptyList(), Collections.emptyList(), apps1);
    Topology t2 = createTopology(name, Collections.emptyList(), Collections.emptyList(), apps2);

    assertEquals(t1, t2);
    assertEquals("hashcode must be equal if objects are equal.", t1.hashCode(), t2.hashCode());
  }


  @Test
  public void testTopologiesAreEqual() {
    doTestSameTopologies(2, 2, 2);
  }

  @Test
  public void testSameTopologiesNoApps() {
    doTestSameTopologies(2, 2, 0);
  }

  @Test
  public void testSameTopologiesNoProviders() {
    doTestSameTopologies(0, 2, 2);
  }

  @Test
  public void testSameTopologiesNoServices() {
    doTestSameTopologies(2, 0, 2);
  }

  @Test
  public void testDifferentAppCount() {
    final String appName       = "aName";
    final String appRole       = "aRole";
    final String appParamName  = "a_key_one";
    final String appParamValue = "a_value_one";
    final String appURL        = "http://host:1234/app";
    List<List<Application>> apps = createAppLists(appName, appRole, appParamName, appParamValue, appURL, 2);

    List<Application> modifiedApps = apps.get(1);
    modifiedApps.add(createApplication("app2", "app2Role", Collections.emptyList(), Collections.emptyMap()));

    assertFalse("Expected inequality because there are a different number of applications.",
                doTestApplicationEquality(apps.get(0), modifiedApps));
  }

  @Test
  public void testDifferentAppNames() {
    final String appName       = "aName";
    final String appRole       = "aRole";
    final String appURL        = "http://host:1234/app";
    Map<String, String> params = Collections.emptyMap();

    // Since applications' roles get set as the name, vary the role for this test
    Application a1 = createApplication(appName, appRole, Collections.singletonList(appURL), params);
    Application a2 = createApplication(appName, "differentRole", Collections.singletonList(appURL), params);

    assertFalse("Expected inequality because there are different application names.",
                doTestApplicationEquality(a1, a2));
  }

  @Test
  public void testDifferentAppURLCount() {
    final String appName       = "aName";
    final String appRole       = "aRole";
    final String appURL        = "http://host:1234/app";
    Map<String, String> params = Collections.emptyMap();

    Application a1 = createApplication(appName, appRole, Collections.singletonList(appURL), params);

    List<String> urls = Arrays.asList(appURL, appURL + "/other");
    Application a2 = createApplication(appName, appRole, urls, params);

    assertFalse("Expected inequality because there are different number of application URLs.",
                doTestApplicationEquality(a1, a2));
  }

  @Test
  public void testDifferentAppURLValues() {
    final String appName       = "aName";
    final String appRole       = "aRole";
    final String appURL        = "http://host:1234/app";
    Map<String, String> params = Collections.emptyMap();

    Application a1 = createApplication(appName, appRole, Collections.singletonList(appURL), params);
    Application a2 = createApplication(appName, appRole, Collections.singletonList(appURL + "/other"), params);

    assertFalse("Expected inequality because there are different application URL values.",
                doTestApplicationEquality(a1, a2));
  }


  @Test
  public void testDifferentAppParamCount() {
    final String appName       = "aName";
    final String appRole       = "aRole";
    final String appParamName  = "a_key_one";
    final String appParamValue = "a_value_one";
    final List<String> urls = Collections.emptyList();

    Map<String, String> params = new HashMap<>();
    params.put(appParamName, appParamValue);
    Application a1 = createApplication(appName, appRole, urls, params);

    Map<String, String> params2 = new HashMap<>();
    params.put(appParamName, appParamValue);
    params.put("anotherName", "anotherValue");
    Application a2 = createApplication(appName, appRole, urls, params2);

    assertFalse("Expected inequality because there are different number of application params.",
                doTestApplicationEquality(a1, a2));
  }

  @Test
  public void testDifferentAppParamValues() {
    final String appName       = "aName";
    final String appRole       = "aRole";
    final String appParamName  = "a_key_one";
    final String appParamValue = "a_value_one";
    final List<String> urls = Collections.emptyList();

    Map<String, String> params = new HashMap<>();
    params.put(appParamName, appParamValue);
    Application a1 = createApplication(appName, appRole, urls, params);

    Map<String, String> params2 = new HashMap<>();
    params.put(appParamName, "anotherValue");
    Application a2 = createApplication(appName, appRole, urls, params2);

    assertFalse("Expected inequality because there are different application param values.",
                doTestApplicationEquality(a1, a2));
  }

  @Test
  public void testDifferentServiceNames() {
    final String serviceName = "sName";
    final String serviceRole = "sRole";
    final String serviceURL = "http://host:1234/service";

    Service s1 = createService(serviceName, serviceRole, Collections.singletonList(serviceURL), Collections.emptyMap());
    Service s2 = createService("another", serviceRole, Collections.singletonList(serviceURL), Collections.emptyMap());

    assertFalse("Expected inequality because there are different service names.",
                doTestServiceEquality(s1, s2));
  }

  @Test
  public void testDifferentServiceCount() {
    final String serviceName = "sName";
    final String serviceRole = "sRole";
    final String serviceURL = "http://host:1234/service";

    Service s1 = createService(serviceName, serviceRole, Collections.singletonList(serviceURL), Collections.emptyMap());
    Service s2 = createService("another", serviceRole, Collections.singletonList(serviceURL), Collections.emptyMap());

    assertFalse("Expected inequality because there are different number of services.",
                doTestServiceEquality(s1, s2));
  }


  @Test
  public void testDifferentServiceURLCount() {
    final String serviceName = "sName";
    final String serviceRole = "sRole";
    final String serviceURL = "http://host:1234/service";

    Service s1 = createService(serviceName, serviceRole, Collections.singletonList(serviceURL), Collections.emptyMap());
    Service s2 =
      createService(serviceName, serviceRole, Arrays.asList(serviceURL, serviceURL + "/other"), Collections.emptyMap());

    assertFalse("Expected inequality because there are a different number of service URLs.",
                doTestServiceEquality(s1, s2));
  }

  @Test
  public void testDifferentServiceURLValues() {
    final String serviceName = "sName";
    final String serviceRole = "sRole";
    final String serviceURL = "http://host:1234/service";

    Service s1 = createService(serviceName, serviceRole, Collections.singletonList(serviceURL), Collections.emptyMap());
    Service s2 =
      createService(serviceName, serviceRole, Collections.singletonList(serviceURL + "/other"), Collections.emptyMap());

    assertFalse("Expected inequality because there are different service URL values.",
                doTestServiceEquality(s1, s2));
  }

  @Test
  public void testDifferentServiceParamCount() {
    final String serviceName = "sName";
    final String serviceRole = "sRole";
    final String serviceURL = "http://host:1234/service";


    Map<String, String> params = new HashMap<>();
    params.put("paramOne", "paramOneValue");
    Service s1 = createService(serviceName, serviceRole, Collections.singletonList(serviceURL), params);

    Map<String, String> params2 = new HashMap<>();
    params2.put("paramOne", "paramOneValue");
    params2.put("paramTwo", "paramTwoValue");
    Service s2 = createService(serviceName, serviceRole, Collections.singletonList(serviceURL), params2);

    assertFalse("Expected inequality because there are different number of service params.",
                doTestServiceEquality(s1, s2));
  }

  @Test
  public void testDifferentServiceParamValues() {
    final String serviceName = "sName";
    final String serviceRole = "sRole";
    final String serviceURL  = "http://host:1234/service";
    final String paramName   = "paramOne";


    Map<String, String> params = new HashMap<>();
    params.put(paramName, "paramValue");
    Service s1 = createService(serviceName, serviceRole, Collections.singletonList(serviceURL), params);

    Map<String, String> params2 = new HashMap<>();
    params2.put(paramName, "paramValue" + "DIFFERENT");
    Service s2 = createService(serviceName, serviceRole, Collections.singletonList(serviceURL), params2);

    assertFalse("Expected inequality because there are different service param values.",
                doTestServiceEquality(s1, s2));
  }

  @Test
  public void testDifferentProviderCount() {
    final String name = "pName";
    final String role = "pRole";

    Provider p1 = createProvider(name, role, Collections.emptyMap());
    Provider p2 = createProvider(name, role, Collections.emptyMap());

    assertFalse("Expected inequality because there are a different number of providers.",
                doTestProviderEquality(Collections.singletonList(p1), Arrays.asList(p2, p1)));
  }

  @Test
  public void testDifferentProviderNames() {
    final String role = "pRole";

    Provider p1 = createProvider("p1", role, Collections.emptyMap());
    Provider p2 = createProvider("p2", role, Collections.emptyMap());

    assertFalse("Expected inequality because there are different provider names.",
                doTestProviderEquality(p1, p2));
  }

  @Test
  public void testDifferentProviderParamCount() {
    final String name = "pName";
    final String role = "pRole";

    Map<String, String> params = new HashMap<>();
    params.put("paramOne", "p1Value");
    Provider p1 = createProvider(name, role, params);

    Map<String, String> params2 = new HashMap<>();
    params2.put("paramOne", "p1Value");
    params2.put("paramTwo", "p2Value");
    Provider p2 = createProvider(name, role, params2);

    assertFalse("Expected inequality because there are a different number of provider params.",
                doTestProviderEquality(p1, p2));
  }

  @Test
  public void testDifferentProviderParamValues() {
    final String name      = "pName";
    final String role      = "pRole";
    final String paramName = "paramOne";

    Map<String, String> params = new HashMap<>();
    params.put(paramName, "p1Value");
    Provider p1 = createProvider(name, role, params);

    Map<String, String> params2 = new HashMap<>();
    params2.put(paramName, "somethingelse");
    Provider p2 = createProvider(name, role, params2);

    assertFalse("Expected inequality because there are a different provider param values.",
                doTestProviderEquality(p1, p2));
  }

  private void doTestSameTopologies(int providerCount, int serviceCount, int appCount) {
    final String providerName       = "pName";
    final String providerRole       = "pRole";
    final String providerParamName  = "p_key_one";
    final String providerParamValue = "p_value_one";
    List<List<Provider>> providers =
        createProviderLists(providerName, providerRole, providerParamName, providerParamValue, providerCount);

    final String serviceName       = "sName";
    final String serviceRole       = "sRole";
    final String serviceParamName  = "s_key_one";
    final String serviceParamValue = "s_value_one";
    final String serviceURL        = "http://host:1234/service";
    final List<List<Service>> services =
        createServiceLists(serviceName, serviceRole, serviceParamName, serviceParamValue, serviceURL, serviceCount);

    final String appName       = "aName";
    final String appRole       = "aRole";
    final String appParamName  = "a_key_one";
    final String appParamValue = "a_value_one";
    final String appURL        = "http://host:1234/app";
    List<List<Application>> apps = createAppLists(appName, appRole, appParamName, appParamValue, appURL, appCount);

    boolean isEqual = doTestEquals(!providers.isEmpty() ? providers.get(0) : Collections.emptyList(),
                                   providers.size() > 1 ? providers.get(1) : Collections.emptyList(),
                                   !services.isEmpty()  ? services.get(0)  : Collections.emptyList(),
                                   services.size()  > 1 ? services.get(1)  : Collections.emptyList(),
                                   !apps.isEmpty()      ? apps.get(0)      : Collections.emptyList(),
                                   apps.size()      > 1 ? apps.get(1)      : Collections.emptyList());
    assertTrue("Expected topologies to be equal.", isEqual);
  }

  private boolean doTestApplicationEquality(Application app1, Application app2) {
    return doTestApplicationEquality(Collections.singletonList(app1), Collections.singletonList(app2));
  }

  private boolean doTestApplicationEquality(List<Application> apps1, List<Application> apps2) {
    return doTestEquals(Collections.emptyList(),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        apps1,
                        apps2);
  }


  private boolean doTestServiceEquality(Service s1, Service s2) {
    return doTestServiceEquality(Collections.singletonList(s1), Collections.singletonList(s2));
  }


  private boolean doTestServiceEquality(List<Service> svcs1, List<Service> svcs2) {
    return doTestEquals(Collections.emptyList(),
                        Collections.emptyList(),
                        svcs1,
                        svcs2,
                        Collections.emptyList(),
                        Collections.emptyList());
  }


  private boolean doTestProviderEquality(Provider p1, Provider p2) {
    return doTestProviderEquality(Collections.singletonList(p1), Collections.singletonList(p2));
  }

  private boolean doTestProviderEquality(List<Provider> p1, List<Provider> p2) {
    return doTestEquals(p1,
                        p2,
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Collections.emptyList());
  }


  /**
   *
   * @param provs1
   * @param provs2
   * @param svcs1
   * @param svcs2
   * @param apps1
   * @param apps2
   *
   * @return true if the topologies are equal; Otherwise, false.
   */
  private boolean doTestEquals(final List<Provider>    provs1,
                               final List<Provider>    provs2,
                               final List<Service>     svcs1,
                               final List<Service>     svcs2,
                               final List<Application> apps1,
                               final List<Application> apps2) {
    final String topoName = "testTopology";
    return doTestEquals(topoName, topoName, provs1, provs2, svcs1, svcs2, apps1, apps2);
  }

  /**
   *
   * @param name1
   * @param name2
   * @param provs1
   * @param provs2
   * @param svcs1
   * @param svcs2
   * @param apps1
   * @param apps2
   *
   * @return true if the topologies are equal; Otherwise, false.
   */
  private boolean doTestEquals(final String            name1,
                               final String            name2,
                               final List<Provider>    provs1,
                               final List<Provider>    provs2,
                               final List<Service>     svcs1,
                               final List<Service>     svcs2,
                               final List<Application> apps1,
                               final List<Application> apps2) {

    Topology t1 = createTopology(name1, provs1, svcs1, apps1);
    Topology t2 = createTopology(name2, provs2, svcs2, apps2);

    boolean result = t1.equals(t2);
    if (result) {
      assertEquals("hashcode must be equal if objects are equal.", t1.hashCode(), t2.hashCode());
    }
    return result;
  }

  private static Topology createTopology(final String            name,
                                         final List<Provider>    providers,
                                         final List<Service>     services,
                                         final List<Application> applications) {

    return createTopology(name, providers, services, applications, null);
  }

  private static Topology createTopology(final String            name,
                                         final List<Provider>    providers,
                                         final List<Service>     services,
                                         final List<Application> applications,
                                         final URI               uri) {

    Topology t = new Topology();
    t.providerList = providers;
    t.services = services;
    t.applications = applications;
    t.setName(name);
    t.setUri(uri);
    return t;
  }

  private List<List<Provider>> createProviderLists(final String providerName,
                                                   final String providerRole,
                                                   final String providerParamName,
                                                   final String providerParamValue,
                                                   int          count) {
    List<List<Provider>> result = new ArrayList<>();

    for (int i=0; i < count ; i++) {
      List<Provider> providers = new ArrayList<>();
      Map<String, String> providerParams = new HashMap<>();
      providerParams.put(providerParamName, providerParamValue);
      providers.add(createProvider(providerName, providerRole, providerParams));
      result.add(providers);
    }

    return result;
  }

  private List<List<Service>> createServiceLists(final String serviceName,
                                                 final String serviceRole,
                                                 final String serviceParamName,
                                                 final String serviceParamValue,
                                                 final String serviceURL,
                                                 int          count) {
    List<List<Service>> result = new ArrayList<>();

    for (int i=0; i < count ; i++) {
      List<Service> svcs = new ArrayList<>();
      Map<String, String> svcParams = new HashMap<>();
      svcParams.put(serviceParamName, serviceParamValue);
      svcs.add(createService(serviceName, serviceRole, Collections.singletonList(serviceURL), svcParams));
      result.add(svcs);
    }

    return result;
  }

  private List<List<Application>> createAppLists(final String appName,
                                                 final String appRole,
                                                 final String appParamName,
                                                 final String appParamValue,
                                                 final String appURL,
                                                 int          count) {
    List<List<Application>> result = new ArrayList<>();

    for (int i=0; i < count ; i++) {
      List<Application> apps = new ArrayList<>();
      Map<String, String> appParams = new HashMap<>();
      appParams.put(appParamName, appParamValue);
      apps.add(createApplication(appName, appRole, Collections.singletonList(appURL), appParams));
      result.add(apps);
    }

    return result;
  }

  private Service createService(final String              name,
                                final String              role,
                                final List<String>        urls,
                                final Map<String, String> params) {
    Service s = new Service();
    s.setName(name);
    s.setRole(role);
    s.setUrls(urls);
    s.setParams(params);
    return s;
  }

  private Provider createProvider(final String              name,
                                  final String              role,
                                  final Map<String, String> params) {
    Provider p = new Provider();
    p.setName(name);
    p.setRole(role);
    p.setParams(params);

    return p;
  }

  private Application createApplication(final String              name,
                                        final String              role,
                                        final List<String>        urls,
                                        final Map<String, String> params) {
    Application a = new Application();
    a.setName(name);
    a.setRole(role);
    a.setUrls(urls);
    a.setParams(params);

    return a;
  }

}
