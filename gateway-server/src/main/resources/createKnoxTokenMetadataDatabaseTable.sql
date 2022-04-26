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

CREATE TABLE IF NOT EXISTS KNOX_TOKEN_METADATA ( -- IF NOT EXISTS syntax is not supported by Derby
   token_id varchar(128) NOT NULL,
   md_name varchar(32) NOT NULL,
   md_value varchar(256) NOT NULL,
   PRIMARY KEY (token_id, md_name),
   CONSTRAINT fk_token_id FOREIGN KEY(token_id) REFERENCES KNOX_TOKENS(token_id) ON DELETE CASCADE
)