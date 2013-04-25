#!/bin/sh

#
#  Licensed to the Apache Software Foundation (ASF) under one or more
#  contributor license agreements.  See the NOTICE file distributed with
#  this work for additional information regarding copyright ownership.
#  The ASF licenses this file to You under the Apache License, Version 2.0
#  (the "License"); you may not use this file except in compliance with
#  the License.  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
#

# target
KNOX_PACKAGE_TARGET=$1
# knox
KNOX_NAME=$2
# 0.3.0-SNAPSHOT
KNOX_VERSION=$3
# target/RPM base directory
KNOX_RPM_BUILD_ROOT="$(pwd)"/$KNOX_PACKAGE_TARGET/RPM


#SOURCES Contains the original sources, patches, and icon files.
#SPECS Contains the spec files used to contrl the build process. 
#The BUILD directory in which the sources are unpacked, and the software is built.
#RPMS Contains the binary package files created by the build process.
#SRPMS Contains the source package files created by the build process.
mkdir -p $KNOX_RPM_BUILD_ROOT/{SOURCES,SPECS,BUILD,RPMS,SRPMS}

cp ./gateway-rpm/knox.spec $KNOX_RPM_BUILD_ROOT/SPECS
cp ./$KNOX_PACKAGE_TARGET/$KNOX_VERSION/$KNOX_NAME-$KNOX_VERSION.tar.gz $KNOX_RPM_BUILD_ROOT/SOURCES

rpmbuild --define "_topdir $KNOX_RPM_BUILD_ROOT" --define "_knox_name $KNOX_NAME" --define "_knox_ver $KNOX_VERSION" -bb $KNOX_RPM_BUILD_ROOT/SPECS/knox.spec 
