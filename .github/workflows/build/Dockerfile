# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to you under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

FROM eclipse-temurin:17-jre

MAINTAINER moresandeep

# Install dependencies
RUN apt-get update && apt-get install -y git && useradd -ms /bin/bash gateway

# Create temporary directories for extraction
RUN mkdir -p /tmp/knox-artifacts /tmp/knoxshell-artifacts /knox-runtime /knoxshell /knox-runtime/knoxshell

# Copy pre-built tarballs from the local target directory
# In CI, these are built in the previous step.
# Locally, these are built by the developer.
COPY target/*/knox-*.tar.gz /tmp/knox-artifacts/
COPY target/*/knoxshell-*.tar.gz /tmp/knoxshell-artifacts/

# Extract and move to runtime location
RUN tar -xvzf /tmp/knox-artifacts/knox-*.tar.gz -C /tmp/knox-artifacts/ && \
    tar -xvzf /tmp/knoxshell-artifacts/knoxshell-*.tar.gz -C /tmp/knoxshell-artifacts/ && \
    mv /tmp/knox-artifacts/knox-*/* /knox-runtime/ && \
    mv /tmp/knoxshell-artifacts/knoxshell-*/* /knox-runtime/knoxshell/ && \
    rm -rf /tmp/knox-artifacts /tmp/knoxshell-artifacts

# Configuration and scripts
ADD .github/workflows/build/master /knox-runtime/data/security/master
ADD .github/workflows/build/gateway-site.xml /knox-runtime/conf/gateway-site.xml
ADD .github/workflows/build/conf/topologies/knoxtoken.xml /knox-runtime/conf/topologies/knoxtoken.xml
ADD .github/workflows/build/conf/topologies/health.xml /knox-runtime/conf/topologies/health.xml
ADD .github/workflows/build/conf/topologies/knoxldap.xml /knox-runtime/conf/topologies/knoxldap.xml
ADD .github/workflows/build/conf/topologies/remoteauth.xml /knox-runtime/conf/topologies/remoteauth.xml
ADD .github/workflows/build/conf/topologies/knoxidf-ldap.xml /knox-runtime/conf/topologies/knoxidf-ldap.xml
ADD .github/workflows/build/conf/topologies/knoxidf-token.xml /knox-runtime/conf/topologies/knoxidf-token.xml

RUN chown -R gateway /knox-runtime/

ADD .github/workflows/build/ldap.sh /ldap.sh
ADD .github/workflows/build/gateway.sh /gateway.sh

RUN chmod +x /ldap.sh
RUN chmod +x /gateway.sh
