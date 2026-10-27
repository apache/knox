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

"""A stand-in Spark Connect server for the Knox integration tests.

Running real Spark would add a gigabyte of image and a minute of startup to
test a gateway, and the gateway does not care what is behind it -- only that it
speaks `spark.connect.SparkConnectService`. So this implements just enough of
that service to make the gateway's behavior observable.

The important trick is that the RPCs echo back what the *backend* received,
rather than returning canned data. Knox overwrites `user_context.user_id` with
the authenticated principal on its way through, and that rewrite is invisible
from the client side -- the client only knows what it sent. By reflecting the
observed identity into the response, an assertion about what Spark would have
seen becomes an ordinary assertion in the test.

The stubs are generated at container start from the same vendored
`spark/connect/*.proto` files the gateway compiles against, so a proto refresh
that broke the wire contract would break this too.
"""

import logging
import os
from concurrent import futures

import grpc

from spark.connect import base_pb2
from spark.connect import base_pb2_grpc

LOG = logging.getLogger("mock-spark-connect")

# Enough responses to prove a server stream is relayed message by message rather
# than collapsed or truncated.
EXECUTE_PLAN_RESPONSE_COUNT = 5


def _observed_user(request):
    """The user_id the backend actually received, i.e. after Knox's rewrite."""
    return request.user_context.user_id


class MockSparkConnectService(base_pb2_grpc.SparkConnectServiceServicer):
    """Implements the handful of RPCs the integration tests exercise."""

    def AnalyzePlan(self, request, context):  # noqa: N802 - gRPC naming
        observed = _observed_user(request)
        LOG.info("AnalyzePlan session=%s user_id=%s", request.session_id, observed)
        # explain_string is a free-form string field, so it can carry the observed
        # identity back to the test without inventing a side channel.
        return base_pb2.AnalyzePlanResponse(
            session_id=request.session_id,
            explain=base_pb2.AnalyzePlanResponse.Explain(explain_string=observed),
        )

    def ExecutePlan(self, request, context):  # noqa: N802 - gRPC naming
        observed = _observed_user(request)
        LOG.info("ExecutePlan session=%s user_id=%s", request.session_id, observed)
        for index in range(EXECUTE_PLAN_RESPONSE_COUNT):
            yield base_pb2.ExecutePlanResponse(
                session_id=request.session_id,
                operation_id=observed,
                response_id=f"response-{index}",
            )

    def Config(self, request, context):  # noqa: N802 - gRPC naming
        observed = _observed_user(request)
        LOG.info("Config session=%s user_id=%s", request.session_id, observed)
        return base_pb2.ConfigResponse(
            session_id=request.session_id,
            # Echo the observed identity as a config value so the Config path can
            # be asserted the same way as AnalyzePlan.
            pairs=[base_pb2.KeyValue(key="knox.observed.user", value=observed)],
        )

    def AddArtifacts(self, request_iterator, context):  # noqa: N802 - gRPC naming
        observed = ""
        count = 0
        for request in request_iterator:
            observed = _observed_user(request)
            count += 1
        LOG.info("AddArtifacts messages=%d user_id=%s", count, observed)
        return base_pb2.AddArtifactsResponse()


def serve():
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    port = os.environ.get("MOCK_PORT", "15002")
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=8))
    base_pb2_grpc.add_SparkConnectServiceServicer_to_server(MockSparkConnectService(), server)
    # Plaintext: this stands in for a Spark Connect server on a private network,
    # which is exactly the posture Knox's grpc:// backend scheme describes.
    server.add_insecure_port(f"[::]:{port}")
    server.start()
    LOG.info("Mock Spark Connect server listening on %s", port)
    server.wait_for_termination()


if __name__ == "__main__":
    serve()
