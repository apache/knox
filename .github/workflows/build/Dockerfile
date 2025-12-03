FROM eclipse-temurin:17-jre

MAINTAINER moresandeep

RUN useradd -ms /bin/bash gateway

# Create directories
RUN mkdir /knox-runtime
RUN mkdir /knox-runtime/knoxshell

# Copy artifacts
COPY knox-temp-artifacts /knox-temp-artifacts-staging
COPY knoxshell-temp-artifacts /knoxshell-temp-artifacts-staging

# Move runtime to new location
RUN mv /knox-temp-artifacts-staging/*/* /knox-runtime/ && \
    mv /knoxshell-temp-artifacts-staging/*/* /knox-runtime/knoxshell/ && \
    rm -rf /knox-temp-artifacts-staging /knoxshell-temp-artifacts-staging

# Add configuration
ADD master /knox-runtime/data/security/master
ADD gateway-site.xml /knox-runtime/conf/gateway-site.xml
ADD conf/topologies/knoxtoken.xml /knox-runtime/conf/topologies/knoxtoken.xml
ADD conf/topologies/health.xml /knox-runtime/conf/topologies/health.xml

RUN chown -R gateway /knox-runtime/

ADD ldap.sh /ldap.sh
ADD gateway.sh /gateway.sh

RUN chmod +x /ldap.sh /gateway.sh
