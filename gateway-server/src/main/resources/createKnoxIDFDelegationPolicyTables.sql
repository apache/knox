--  Licensed to the Apache Software Foundation (ASF) under one or more
--  contributor license agreements. See the NOTICE file distributed with this
--  work for additional information regarding copyright ownership. The ASF
--  licenses this file to you under the Apache License, Version 2.0 (the
--  "License"); you may not use this file except in compliance with the License.
--  You may obtain a copy of the License at
--
--  http://www.apache.org/licenses/LICENSE-2.0
--
--  Unless required by applicable law or agreed to in writing, software
--  distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
--  WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
--  License for the specific language governing permissions and limitations under
--  the License.

CREATE TABLE IF NOT EXISTS DELEGATION_POLICIES (
    registration_id         VARCHAR(36)   NOT NULL,
    actor_authority         VARCHAR(20)   NOT NULL,
    actor_id                VARCHAR(2048) NOT NULL,
    name                    VARCHAR(256),
    status                  VARCHAR(20)   DEFAULT 'active' NOT NULL,
    max_token_ttl_sec       INTEGER,
    description             VARCHAR(1024),
    created_by              VARCHAR(2048),
    created_at              TIMESTAMP     NOT NULL,
    updated_at              TIMESTAMP     NOT NULL,
    allow_headless_exchange BOOLEAN       DEFAULT FALSE NOT NULL,
    PRIMARY KEY (registration_id),
    CONSTRAINT UX_DELEGATION_ACTOR UNIQUE (actor_authority, actor_id)
);

CREATE TABLE IF NOT EXISTS DELEGATION_POLICY_USERS (
    registration_id VARCHAR(36)   NOT NULL REFERENCES DELEGATION_POLICIES(registration_id) ON DELETE CASCADE,
    username        VARCHAR(1023) NOT NULL,
    PRIMARY KEY (registration_id, username)
);

CREATE TABLE IF NOT EXISTS DELEGATION_POLICY_GROUPS (
    registration_id VARCHAR(36)   NOT NULL REFERENCES DELEGATION_POLICIES(registration_id) ON DELETE CASCADE,
    group_name      VARCHAR(1023) NOT NULL,
    PRIMARY KEY (registration_id, group_name)
);

CREATE TABLE IF NOT EXISTS DELEGATION_POLICY_RESOURCES (
    registration_id VARCHAR(36)    NOT NULL REFERENCES DELEGATION_POLICIES(registration_id) ON DELETE CASCADE,
    resource_uri    VARCHAR(1023)  NOT NULL,
    PRIMARY KEY (registration_id, resource_uri)
);

CREATE TABLE IF NOT EXISTS DELEGATION_POLICY_RESOURCE_SCOPES (
    registration_id VARCHAR(36)   NOT NULL,
    resource_uri    VARCHAR(1023) NOT NULL,
    scope           VARCHAR(255)  NOT NULL,
    PRIMARY KEY (registration_id, resource_uri, scope),
    FOREIGN KEY (registration_id, resource_uri)
        REFERENCES DELEGATION_POLICY_RESOURCES(registration_id, resource_uri) ON DELETE CASCADE
);
