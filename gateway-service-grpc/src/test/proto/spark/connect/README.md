<!--
   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
-->

# Vendored Spark Connect protocol definitions

These `.proto` files are copied verbatim from Apache Spark. They are **not**
maintained here — do not edit them. Changes belong upstream.

## Provenance

|             |                                                                     |
|-------------|---------------------------------------------------------------------|
| Repository  | <https://github.com/apache/spark>                                   |
| Path        | `sql/connect/common/src/main/protobuf/spark/connect/`               |
| Tag         | `v4.2.0`                                                            |
| Commit      | `32f7299601108917fb01920a54e084595b7b3bf8`                          |
| Commit date | 2026-07-11                                                          |
| License     | Apache License 2.0 (same as Knox; each file retains its ASF header) |

Every file in this directory is byte-identical to that commit.

## What is here, and what is not

All ten protos that make up the `spark.connect.SparkConnectService` protocol are
vendored. Upstream also carries `example_plugins.proto`, which is **deliberately
not copied** — it is sample code for Spark's own extension mechanism and defines
nothing the gateway proxies.

`base.proto` also imports `google/protobuf/any.proto` and
`google/protobuf/timestamp.proto`. Those are not vendored; they ship inside
`protobuf-java` and protoc resolves them from there.

## Why vendored rather than a dependency

Depending on `org.apache.spark:spark-connect-common` would pull Spark's whole
dependency tree onto the gateway classpath for the sake of a handful of message
definitions. Vendoring the protos and generating with protoc at build time keeps
Knox's classpath its own. `protobuf-java` is already managed in the root pom, and
the grpc-java version is pinned to the line that matches it.

## Drift policy

These track the newest supported Spark line. Skew is expected and mostly
harmless, because the gateway only reads and writes `user_context` and
`session_id` and otherwise round-trips messages through the generated classes. 
Protobuf preserves unknown fields across a parse and re-serialize, so a field
added in a newer Spark survives the trip untouched. Requests for RPCs added after
this snapshot are relayed as opaque bytes by `PassthroughHandlerRegistry`; they
lose identity assertion but are still authenticated, authorized, routed and
audited.

The uniformity the gateway relies on is that every `SparkConnectService` request
message carries `string session_id = 1` and `UserContext user_context = 2` in the
same positions. `SparkConnectMessageInterceptorTest` asserts this across the RPC
shapes, so a refresh that broke the assumption would fail the build rather than
silently stop asserting identity.

## Refreshing

Set the tag you want, re-copy, and rerun the build:

```bash
SPARK_TAG=v4.2.0
DEST=gateway-service-sparkconnect/src/main/proto/spark/connect
BASE=https://raw.githubusercontent.com/apache/spark/$SPARK_TAG/sql/connect/common/src/main/protobuf/spark/connect

for f in base catalog commands common expressions ml ml_common pipelines relations types; do
  curl -fsS -o "$DEST/$f.proto" "$BASE/$f.proto"
done
```

Then update the provenance table above with the new tag and the commit it
resolves to:

```bash
curl -fsS "https://api.github.com/repos/apache/spark/git/ref/tags/$SPARK_TAG" \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["object"]["sha"])'
```

After refreshing, check whether upstream added any RPC to `SparkConnectService`.
New methods are picked up automatically — handlers are registered by iterating
the generated service descriptor, not from a handwritten list — but a new RPC
whose request message departs from the `session_id`/`user_context` shape would
need the interceptor revisited.
