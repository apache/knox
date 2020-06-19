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
package org.apache.knox.gateway.pac4j.config;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.easymock.EasyMock;
import org.junit.Test;
import org.pac4j.core.client.Client;

public class Pac4jClientConfigurationDecoratorTest {

  @Test
  public void testClientConfigDecoration() throws Exception {
    final AtomicInteger tested = new AtomicInteger(0);
    final AtomicInteger decorated = new AtomicInteger(0);

    final ClientConfigurationDecorator passiveDecorator = new TestClientConfigurationDecorator(tested, decorated, false);
    final ClientConfigurationDecorator activeDecorator = new TestClientConfigurationDecorator(tested, decorated, true);
    final Pac4jClientConfigurationDecorator pac4jConfigurationDecorator = new Pac4jClientConfigurationDecorator(Arrays.asList(passiveDecorator, activeDecorator));
    final Client client = EasyMock.createNiceMock(Client.class);
    pac4jConfigurationDecorator.decorateClients(Collections.singletonList(client), null);
    assertEquals(2, tested.get());
    assertEquals(1, decorated.get());
  }

  private static class TestClientConfigurationDecorator implements ClientConfigurationDecorator {

    private final AtomicInteger tested;
    private final AtomicInteger decorated;
    private final boolean decorate;

    TestClientConfigurationDecorator(AtomicInteger testedClientsNum, AtomicInteger decoratedClientsNum, boolean decorate) {
      this.tested = testedClientsNum;
      this.decorated = decoratedClientsNum;
      this.decorate = decorate;
    }

    @Override
    public void decorateClients(List<Client> clients, Map<String, String> properties) {
      clients.forEach(client -> {
        tested.incrementAndGet();
        if (decorate) {
          decorated.incrementAndGet();
        }
      });
    }
  }

}
