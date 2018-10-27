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

import org.apache.knox.gateway.shell.KnoxSession
import org.apache.knox.gateway.shell.manager.Manager
import org.apache.knox.gateway.shell.Credentials

GATEWAY = "https://localhost:8443/gateway"
SAMPLES_DIR = "./samples"
SAMPLE_PROVIDER_CONFIG_SOURCE = SAMPLES_DIR + "/sample-providers.json"
SAMPLE_DESCRIPTOR_SOURCE = SAMPLES_DIR + "/sample-descriptor.json"

credentials = new Credentials()
credentials.add("ClearInput", "Enter username: ", "user")
           .add("HiddenInput", "Enter pas" + "sword: ", "pass")
credentials.collect()

username = credentials.get("user").string()
pass = credentials.get("pass").string()

session = KnoxSession.login( GATEWAY, username, pass )

// Present the existing provider configurations
List<String> pcs = Manager.listProviderConfigurations(session);
System.out.println("\nExisting Provider Configurations")
for (String pc : pcs) {
  System.out.println("  \u2022 " + pc)
}

// Deploy a new provider configuration
Manager.deployProviderConfiguration(session, "sample-providers", SAMPLE_PROVIDER_CONFIG_SOURCE)

// Present the updated set of provider configurations
pcs = Manager.listProviderConfigurations(session);
System.out.println("\nProvider Configurations After Deployment")
for (String pc : pcs) {
  System.out.println("  \u2022 " + pc)
}

// Present the set of existing descriptors
List<String> descs = Manager.listDescriptors(session);
System.out.println("\nExisting Descriptors")
for (String desc : descs) {
  System.out.println("  \u2022 " + desc)
}

// Deploy a new descriptor
Manager.deployDescriptor(session, "sample", SAMPLE_DESCRIPTOR_SOURCE)

// Present the set of descriptors, showing that the deployment succeeded
descs = Manager.listDescriptors(session);
System.out.println("\nDescriptors After Deployment")
for (String desc : descs) {
  System.out.println("  \u2022 " + desc)
}

Manager.undeployDescriptor(session, "sample")

// Present the set of descriptors, showing that the undeployment succeeded
descs = Manager.listDescriptors(session);
System.out.println("\nDescriptors After Undeployment")
for (String desc : descs) {
  System.out.println("  \u2022 " + desc)
}

Manager.undeployProviderConfiguration(session, "sample-providers")

// Present the set of provider configurations, showing that the undeployment succeeded
pcs = Manager.listProviderConfigurations(session);
System.out.println("\nProvider Configurations After Undeployment")
for (String pc : pcs) {
  System.out.println("  \u2022 " + pc)
}

System.out.println();

session.shutdown()
