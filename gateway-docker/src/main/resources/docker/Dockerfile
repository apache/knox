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
FROM dhi.io/eclipse-temurin:17-jdk-debian13-dev AS build
LABEL maintainer="Apache Knox <dev@knox.apache.org>"

ARG TARGETARCH
RUN echo "Building Apache Knox Gateway for architecture ${TARGETARCH}"

USER root
# Make sure required packages are available
RUN apt-get update && \
    apt-get upgrade -y && \
    apt-get install -y \
       openssl \
       procps \
       ca-certificates \
       unzip \
       libnss3 \
       bash \
       passwd \
       curl && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Create knox user and group
RUN groupadd --system -g 8000 knox && useradd --system -u 8000 -g knox -d /home/knox -m knox

# Dependencies
ARG RELEASE_FILE
# Using GID 8000 for the knox group to allow arbitrary UIDs with this GID -required for OpenShift/SCC
ADD --chown=8000:0 ${RELEASE_FILE} /home/knox/

# Extract the Knox release tar.gz
RUN chmod 644 /home/knox/*.zip && \
    cd /home/knox && unzip /home/knox/*.zip && rm -f /home/knox/*.zip && ln -nsf /home/knox/*/ /home/knox/knox

# Prepare Knox runtime directories for arbitrary UID support (OpenShift/SCC pattern).
RUN mkdir -p /home/knox/knox/data/security/keystores && \
    mkdir -p /home/knox/knox/conf

# Add the entrypoint script
ARG ENTRYPOINT
COPY --chown=8000:0 ${ENTRYPOINT} /home/knox/knox/entrypoint.sh
RUN chmod +x /home/knox/knox/entrypoint.sh

# Add the Amazon Root CA and Let's Encrypt production certificates (best-effort)
RUN mkdir /home/knox/cacrts && \
    curl -sSLo /home/knox/cacrts/AmazonRootCA1.cer  https://www.amazontrust.com/repository/AmazonRootCA1.cer  || true && \
    curl -sSLo /home/knox/cacrts/AmazonRootCA2.cer  https://www.amazontrust.com/repository/AmazonRootCA2.cer  || true && \
    curl -sSLo /home/knox/cacrts/AmazonRootCA3.cer  https://www.amazontrust.com/repository/AmazonRootCA3.cer  || true && \
    curl -sSLo /home/knox/cacrts/AmazonRootCA4.cer  https://www.amazontrust.com/repository/AmazonRootCA4.cer  || true && \
    curl -sSLo /home/knox/cacrts/isrgrootx1.pem     https://letsencrypt.org/certs/isrgrootx1.pem               || true && \
    curl -sSLo /home/knox/cacrts/isrg-root-x2.pem   https://letsencrypt.org/certs/isrg-root-x2.pem             || true && \
    chown -R 8000:0 /home/knox/cacrts

WORKDIR /home/knox/knox

# Expose the default port as a convenience
ARG EXPOSE_PORT
EXPOSE ${EXPOSE_PORT}

# Ensure all files are owned by the knox user and the root group (for arbitrary UID support)
RUN chown -R 8000:0 /home/knox && \
# Recursively sets group mode = user mode, whatever permission bits apply to the owner will be copied to the group - required for OpenShift/SCC
    chmod -R g=u /home/knox

# Switch off of the root user
USER 8000:0
ENTRYPOINT ["./entrypoint.sh"]
