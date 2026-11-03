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

"""Mock roles REST API for the LDAP proxy roles lookup E2E tests (strategy=rest).

Serves the role assignments of a roles.json file (the same format, and by default the same
file, the file strategy reads) through the three endpoints RestApiLdapRolesLookup and
RestApiLdapRolesReverseLookup call:

* POST /api/v1/roles/lookup  {"user_id", "groups"} -> {"user_id", "roles": [{scope, name}]}
* GET  /api/v1/roles?offset=&name=&scope=        -> {"data": [{id, name, scope}], "pagination"}
* GET  /api/v1/roles/<id>                        -> {id, name, scope, "members": [...]}

User ids are matched case-sensitively, like a roles backend that stores the user id as it was
assigned; role names and scopes are not. Role lists are returned in pages of PAGE_SIZE so that
the client's paging is exercised. Standard library only, so it runs on the same python image as
the tests.
"""

from __future__ import annotations

import json
import os
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

ROLES_FILE = os.environ.get("ROLES_FILE", "/data/roles.json")
PORT = int(os.environ.get("PORT", "8080"))
PAGE_SIZE = 2
ROLES_PATH = "/api/v1/roles"
LOOKUP_PATH = f"{ROLES_PATH}/lookup"


def load_roles(path: str) -> tuple[list[dict], list[dict]]:
    """Read the role assignments and derive the distinct roles, each with an id and members."""
    with open(path, encoding="utf-8") as roles_file:
        assignments = json.load(roles_file)
    roles: dict[tuple[str, str], dict] = {}
    for assignment in assignments:
        for role in assignment.get("roles", []):
            key = (role.get("scope"), role["name"])
            if key not in roles:
                roles[key] = {"id": f"role-{len(roles) + 1}", "scope": key[0], "name": key[1],
                              "members": []}
            members = roles[key]["members"]
            members.append({"id": f"{roles[key]['id']}-member-{len(members) + 1}",
                            "member_type": assignment["type"],
                            "member_id": assignment["id"]})
    return assignments, list(roles.values())


ASSIGNMENTS, ROLES = load_roles(ROLES_FILE)


def lookup_roles(user_id: str | None, groups: list[str]) -> list[dict]:
    """The roles assigned to the user (exact, case-sensitive id) or to any of the groups."""
    found = []
    for assignment in ASSIGNMENTS:
        if assignment["type"] == "user":
            matches = user_id is not None and assignment["id"] == user_id
        else:
            matches = assignment["id"] in groups
        if matches:
            for role in assignment.get("roles", []):
                if role not in found:
                    found.append(role)
    return found


def list_roles(query: dict[str, list[str]]) -> dict:
    """One page of the roles, optionally filtered by name and scope (case-insensitively)."""
    name = query.get("name", [None])[0]
    scope = query.get("scope", [None])[0]
    offset = int(query.get("offset", ["0"])[0])
    matching = [role for role in ROLES
                if (name is None or role["name"].lower() == name.lower())
                and (scope is None or (role["scope"] or "").lower() == scope.lower())]
    page = matching[offset:offset + PAGE_SIZE]
    return {"data": [{key: role[key] for key in ("id", "name", "scope")} for role in page],
            "pagination": {"limit": PAGE_SIZE, "offset": offset, "total": len(matching)}}


class RolesApiHandler(BaseHTTPRequestHandler):
    """Routes the roles lookup, list and get requests."""

    def do_POST(self) -> None:  # pylint: disable=invalid-name
        """Roles lookup."""
        if urlparse(self.path).path != LOOKUP_PATH:
            self.send_json(404, {"error": "not found"})
            return
        body = json.loads(self.rfile.read(int(self.headers.get("Content-Length", "0"))) or "{}")
        user_id = body.get("user_id")
        roles = lookup_roles(user_id, body.get("groups") or [])
        self.send_json(200, {"user_id": user_id, "roles": roles})

    def do_GET(self) -> None:  # pylint: disable=invalid-name
        """Roles list, role get and the health check."""
        url = urlparse(self.path)
        if url.path == "/health":
            self.send_json(200, {"status": "ok"})
        elif url.path == ROLES_PATH:
            self.send_json(200, list_roles(parse_qs(url.query)))
        elif url.path.startswith(f"{ROLES_PATH}/"):
            role_id = url.path[len(ROLES_PATH) + 1:]
            role = next((role for role in ROLES if role["id"] == role_id), None)
            if role is None:
                self.send_json(404, {"error": f"role {role_id} not found"})
            else:
                self.send_json(200, role)
        else:
            self.send_json(404, {"error": "not found"})

    def send_json(self, status: int, payload: dict) -> None:
        """Write a JSON response."""
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)


if __name__ == "__main__":
    print(f"Serving {len(ROLES)} roles from {ROLES_FILE} on port {PORT}", flush=True)
    ThreadingHTTPServer(("", PORT), RolesApiHandler).serve_forever()
