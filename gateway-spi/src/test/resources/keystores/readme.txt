##########################################################################
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
##########################################################################

server-keystore.jks
  Keystore password: horton
  identity key alias: server
  identity key password: horton

----

server-truststore.jks
  Keystore password: horton
  trusted cert alias: client

----

testSigningKeyName.jks
  Keystore password: testSigningKeyPassphrase
  Signing key alias: testSigningKeyAlias
  Signing key password: testSigningKeyPassphrase

  keytool -genkey -alias testSigningKeyAlias -keyalg RSA -keystore testSigningKeyName.jks \
    -storepass testSigningKeyPassphrase -keypass testSigningKeyPassphrase -keysize 2048 \
    -dname 'CN=testSigningKey,OU=example,O=Apache,L=US,ST=CA,C=US' -noprompt

