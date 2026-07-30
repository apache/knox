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

"""Integration tests for the Spark Connect (gRPC) listener.

These drive a real gRPC client against a running gateway, which is the only way
to cover the parts unit tests have to mock: the listener being discovered and
started, TLS from the gateway identity, real token validation, topology
deployment, and the interceptor chain in its real order.

The backend is a stand-in Spark Connect server that echoes back the
`user_context.user_id` it received. That is what makes identity assertion
observable -- the client cannot otherwise see what Knox rewrote on the way
through.
"""

# Protobuf message classes are created dynamically from the descriptor pool when
# the generated modules are imported, so static analysis cannot see them.
# pylint: disable=no-member

import os
import ssl
import unittest

import grpc
import requests
import urllib3

from spark.connect import base_pb2
from spark.connect import base_pb2_grpc

# The dev environment uses self-signed certificates throughout.
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

TOPOLOGY_METADATA_KEY = "knox-topology"
OPEN_TOPOLOGY = "sparkconnect"
RESTRICTED_TOPOLOGY = "sparkconnect-restricted"
# Present in the demo LDAP the compose environment starts.
KNOX_USER = "guest"
KNOX_PASSWORD = "guest-password"


def _gateway_url():
    return os.environ.get("KNOX_GATEWAY_URL", "https://localhost:8443/")


def _sparkconnect_endpoint():
    host = os.environ.get("KNOX_SPARKCONNECT_HOST", "localhost")
    port = os.environ.get("KNOX_SPARKCONNECT_PORT", "15002")
    return host, int(port)


def _acquire_token():
    """Gets a Knox JWT the way a user would, over HTTPS before any gRPC call.

    The knoxldap topology fronts KNOXTOKEN with a basic-auth Shiro realm over the
    demo LDAP, which is the closest thing this environment has to the
    authenticate-once-then-carry-a-token flow the gRPC listener expects.
    """
    url = f"{_gateway_url()}gateway/knoxldap/knoxtoken/api/v1/token"
    response = requests.get(url, auth=(KNOX_USER, KNOX_PASSWORD), verify=False, timeout=30)
    response.raise_for_status()
    return response.json()["access_token"]


class SparkConnectTestBase(unittest.TestCase):
    """Shared channel plumbing for the Spark Connect listener tests."""

    token = None
    server_certificate = None

    @classmethod
    def setUpClass(cls):
        host, port = _sparkconnect_endpoint()
        # The listener presents the gateway identity, which is self-signed here.
        # Trust exactly that certificate rather than disabling verification, so
        # the test still proves TLS is actually working.
        cls.server_certificate = ssl.get_server_certificate((host, port)).encode("utf-8")
        cls.token = _acquire_token()

    def _channel(self, token=None, topology=None):
        host, port = _sparkconnect_endpoint()
        credentials = grpc.ssl_channel_credentials(root_certificates=self.server_certificate)
        # The gateway certificate is issued for its own hostname, which need not
        # match the compose service name.
        options = (("grpc.ssl_target_name_override", "localhost"),)
        channel = grpc.secure_channel(f"{host}:{port}", credentials, options)
        metadata = []
        if token is not None:
            metadata.append(("authorization", f"Bearer {token}"))
        if topology is not None:
            metadata.append((TOPOLOGY_METADATA_KEY, topology))
        return channel, tuple(metadata)

    def _analyze(self, token=None, topology=None, session_id="itest-session", claimed_user="root"):
        """Sends AnalyzePlan and returns the response, or raises RpcError."""
        channel, metadata = self._channel(token=token, topology=topology)
        with channel:
            stub = base_pb2_grpc.SparkConnectServiceStub(channel)
            request = base_pb2.AnalyzePlanRequest(session_id=session_id)
            # Claim to be someone else; Knox must overwrite this.
            request.user_context.user_id = claimed_user
            return stub.AnalyzePlan(request, metadata=metadata, timeout=30)

    def assert_rpc_code(self, expected, callable_obj):
        """Asserts a call fails with a specific gRPC status code."""
        with self.assertRaises(grpc.RpcError) as raised:
            callable_obj()
        self.assertEqual(expected, raised.exception.code(),
                         f"expected {expected}, got {raised.exception.code()}: "
                         f"{raised.exception.details()}")


class TestSparkConnectAuthentication(SparkConnectTestBase):
    """Nothing reaches the backend without a valid Knox token."""

    def test_call_without_a_token_is_rejected(self):
        """An unauthenticated call must never reach the backend."""
        self.assert_rpc_code(grpc.StatusCode.UNAUTHENTICATED,
                           lambda: self._analyze(token=None, topology=OPEN_TOPOLOGY))

    def test_call_with_a_malformed_token_is_rejected(self):
        """Something that is not a JWT at all is refused cleanly."""
        self.assert_rpc_code(grpc.StatusCode.UNAUTHENTICATED,
                           lambda: self._analyze(token="not-a-jwt", topology=OPEN_TOPOLOGY))

    def test_call_with_a_well_formed_but_unsigned_token_is_rejected(self):
        """A forged JWT is refused as UNAUTHENTICATED, not UNKNOWN."""
        # Structurally a JWT, but not one this gateway issued.
        forged = ("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"
                  ".eyJzdWIiOiJndWVzdCIsImlzcyI6IktOT1hTU08ifQ"
                  ".c2lnbmF0dXJl")
        self.assert_rpc_code(grpc.StatusCode.UNAUTHENTICATED,
                           lambda: self._analyze(token=forged, topology=OPEN_TOPOLOGY))


class TestSparkConnectRouting(SparkConnectTestBase):
    """Topology selection comes from client metadata and must resolve."""

    def test_call_without_a_topology_is_rejected(self):
        """With no default topology configured there is nowhere to route."""
        # No default topology is configured, so there is nowhere to route.
        self.assert_rpc_code(grpc.StatusCode.UNIMPLEMENTED,
                           lambda: self._analyze(token=self.token, topology=None))

    def test_call_to_an_unknown_topology_is_rejected(self):
        """A topology that declares no SPARKCONNECT service is unroutable."""
        self.assert_rpc_code(grpc.StatusCode.UNAVAILABLE,
                           lambda: self._analyze(token=self.token, topology="no-such-topology"))


class TestSparkConnectAuthorization(SparkConnectTestBase):
    """Naming a topology is not the same as being allowed to use it."""

    def test_topology_acl_denies_an_authenticated_user(self):
        """A valid token does not by itself grant access to a topology."""
        # Same valid token, same backend -- refused by that topology's ACL.
        self.assert_rpc_code(grpc.StatusCode.PERMISSION_DENIED,
                           lambda: self._analyze(token=self.token, topology=RESTRICTED_TOPOLOGY))

    def test_permitted_topology_is_reachable_with_the_same_token(self):
        """The same token reaches a topology whose ACLs permit the user."""
        response = self._analyze(token=self.token, topology=OPEN_TOPOLOGY)
        self.assertEqual("itest-session", response.session_id)


class TestSparkConnectIdentityAssertion(SparkConnectTestBase):
    """The client's claimed identity is replaced with the authenticated one."""

    def test_backend_sees_the_authenticated_principal_not_the_claim(self):
        """Knox overwrites the client-supplied user_id before the backend sees it."""
        response = self._analyze(token=self.token, topology=OPEN_TOPOLOGY, claimed_user="root")
        # The mock echoes back the user_id it received.
        self.assertEqual(KNOX_USER, response.explain.explain_string)

    def test_claim_is_overwritten_even_when_left_empty(self):
        """An absent claim is filled in rather than passed through empty."""
        response = self._analyze(token=self.token, topology=OPEN_TOPOLOGY, claimed_user="")
        self.assertEqual(KNOX_USER, response.explain.explain_string)

    def test_identity_is_asserted_on_the_config_rpc_too(self):
        """Identity assertion applies to every RPC, not just AnalyzePlan."""
        channel, metadata = self._channel(token=self.token, topology=OPEN_TOPOLOGY)
        with channel:
            stub = base_pb2_grpc.SparkConnectServiceStub(channel)
            request = base_pb2.ConfigRequest(session_id="itest-config")
            request.user_context.user_id = "root"
            request.operation.get_all.SetInParent()
            response = stub.Config(request, metadata=metadata, timeout=30)
        self.assertEqual(KNOX_USER, response.pairs[0].value)


class TestSparkConnectStreaming(SparkConnectTestBase):
    """Server streaming is relayed message by message."""

    def test_execute_plan_relays_every_response(self):
        """A server stream arrives complete and in order."""
        channel, metadata = self._channel(token=self.token, topology=OPEN_TOPOLOGY)
        with channel:
            stub = base_pb2_grpc.SparkConnectServiceStub(channel)
            request = base_pb2.ExecutePlanRequest(session_id="itest-stream")
            request.user_context.user_id = "root"
            responses = list(stub.ExecutePlan(request, metadata=metadata, timeout=30))
        self.assertEqual(5, len(responses))
        self.assertEqual("response-0", responses[0].response_id)
        self.assertEqual("response-4", responses[-1].response_id)
        # The backend reflects the asserted identity into operation_id.
        self.assertEqual(KNOX_USER, responses[0].operation_id)


class TestSparkConnectMessageGating(SparkConnectTestBase):
    """Per-RPC gating is enforced at the gateway, before the backend."""

    def test_add_artifacts_is_denied_when_configured_to_deny(self):
        """Artifact upload gating is enforced at the gateway."""
        def upload():
            channel, metadata = self._channel(token=self.token, topology=OPEN_TOPOLOGY)
            with channel:
                stub = base_pb2_grpc.SparkConnectServiceStub(channel)
                request = base_pb2.AddArtifactsRequest(session_id="itest-artifacts")
                request.user_context.user_id = "root"
                return stub.AddArtifacts(iter([request]), metadata=metadata, timeout=30)

        # gateway.sparkconnect.add.artifacts.mode is DENY in gateway-site.xml.
        self.assert_rpc_code(grpc.StatusCode.PERMISSION_DENIED, upload)

    def test_reserved_config_key_cannot_be_set_by_a_client(self):
        """Clients cannot write the session keys Knox reserves for itself."""
        def set_reserved():
            channel, metadata = self._channel(token=self.token, topology=OPEN_TOPOLOGY)
            with channel:
                stub = base_pb2_grpc.SparkConnectServiceStub(channel)
                request = base_pb2.ConfigRequest(session_id="itest-reserved")
                request.user_context.user_id = "root"
                pair = request.operation.set.pairs.add()
                pair.key = "knox.principal"
                pair.value = "root"
                return stub.Config(request, metadata=metadata, timeout=30)

        self.assert_rpc_code(grpc.StatusCode.PERMISSION_DENIED, set_reserved)


if __name__ == "__main__":
    unittest.main()
