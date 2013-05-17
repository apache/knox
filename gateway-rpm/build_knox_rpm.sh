#!/bin/bash

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
KNOX_VER=$3
# target/RPM base directory
KNOX_TOPDIR="$(pwd)"/$KNOX_PACKAGE_TARGET/RPM
# Hardware platform
KNOX_BUILD_ARCH="$(arch)"
#RPM name
KNOX_RPMFILENAME="$KNOX_NAME-$KNOX_VER.rpm"

#SOURCES Contains the original sources, patches, and icon files.
#SPECS Contains the spec files used to contrl the build process. 
#The BUILD directory in which the sources are unpacked, and the software is built.
#RPMS Contains the binary package files created by the build process.
#SRPMS Contains the source package files created by the build process.
mkdir -p $KNOX_TOPDIR/{SOURCES,SPECS,BUILD,RPMS,SRPMS}

cp ./gateway-rpm/knox.spec $KNOX_TOPDIR/SPECS
cp ./$KNOX_PACKAGE_TARGET/$KNOX_VER/$KNOX_NAME-$KNOX_VER.tar.gz $KNOX_TOPDIR/SOURCES

rpmbuild --define "_rpmfilename $KNOX_RPMFILENAME" --define "_topdir $KNOX_TOPDIR" --define "_knox_name $KNOX_NAME" --define "_knox_ver $KNOX_VER" --define "_build_arch $KNOX_BUILD_ARCH" --bb $KNOX_TOPDIR/SPECS/knox.spec  

cp $KNOX_TOPDIR/RPMS/$KNOX_RPMFILENAME $KNOX_PACKAGE_TARGET/$KNOX_VER
