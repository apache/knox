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

CREATE TABLE FEDERATED_IDENTITY (
    id               VARCHAR(36) PRIMARY KEY,
    user_id          VARCHAR(36)  NOT NULL,
    provider         VARCHAR(64)  NOT NULL,
    external_subject VARCHAR(255) NOT NULL,
    external_issuer  VARCHAR(255) NOT NULL,
    created_at       TIMESTAMP    NOT NULL
);

CREATE UNIQUE INDEX UX_FED_IDENTITY ON FEDERATED_IDENTITY (provider, external_issuer, external_subject);