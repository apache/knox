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

FROM openjdk:8-jre-alpine3.8
MAINTAINER Apache Knox <dev@knox.apache.org>

USER root
# Make sure required packages are available
RUN apk upgrade --no-cache && \
    apk add --no-cache openssl \
       procps \
       ca-certificates \
       unzip \
        nss && \
    apk add --no-cache bash

# Create knox user and group
# Using GID 8000 for the knox group to allow arbitrary UIDs with this GID
RUN groupadd --system -g 8000 knox && adduser --system -u 8000 -g knox -h /home/knox knox

# Dependencies
ARG RELEASE_FILE
ADD --chown=knox:knox ${RELEASE_FILE} /home/knox/

# Extract the Knox release tar.gz
RUN chmod 644 /home/knox/*.zip && \
    cd /home/knox && unzip /home/knox/*.zip && rm -f /home/knox/*.zip && ln -nsf /home/knox/*/ /home/knox/knox

# Make sure knox owns its files and make all directories group-accessible for arbitrary UIDs
RUN mkdir -p /home/knox/knox/data/security/keystores && \
    mkdir -p /home/knox/knox/conf && \
    chown -R knox:knox /home/knox && \
    chmod -R g+rwX /home/knox \

# Add the entrypoint script
ARG ENTRYPOINT
COPY ${ENTRYPOINT} /home/knox/knox/entrypoint.sh
RUN chmod +x /home/knox/knox/entrypoint.sh

# Add the Amazon Root CA certificates
RUN mkdir /home/knox/cacrts
ADD --chown=knox:knox \
    http://www.awstrust.com/repository/AmazonRootCA1.cer \
    http://www.awstrust.com/repository/AmazonRootCA2.cer \
    http://www.awstrust.com/repository/AmazonRootCA3.cer \
    http://www.awstrust.com/repository/AmazonRootCA4.cer \
    http://letsencrypt.org/certs/staging/letsencrypt-stg-root-x1.pem \
    https://letsencrypt.org/certs/isrgrootx1.pem \
    https://letsencrypt.org/certs/isrg-root-x2.pem /home/knox/cacrts/

WORKDIR /home/knox/knox

# Expose the default port as a convenience
ARG EXPOSE_PORT
EXPOSE ${EXPOSE_PORT}

# Switch off of the root user
USER knox
ENTRYPOINT ["./entrypoint.sh"]
