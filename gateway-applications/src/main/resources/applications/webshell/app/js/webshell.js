/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

function WebShellClient(options, terminal) {
    this.username = options.username;
    this.endpoint = 'wss://'+ options.host + ':' + options.port + '/gateway/homepage/webshell/webshellws?' + options.username;
};

// todo: maybe replace this part with xterm's AttachAddon
WebShellClient.prototype.connect = function () {
    console.log('connecting websocket endpoint:' + this.endpoint);

    if (window.WebSocket) {
        // When new WebSocket(url) is created, it starts connecting immediately
        this.connection = new WebSocket(this.endpoint);
    }else {
        this.terminal.write('WebSocket Not Supported');
        return;
    }
    this.terminal.write('Connecting ...\r\n');

    this.connection.onopen = function () {
        console.log('WebSocket connection established');
    };

    this.connection.onmessage = function (event) {
        this.terminal.write(event.data);
    };

    this.connection.onerror = function (event) {
        this.terminal.write(event.data);
    };


    this._connection.onclose = function (event) {
        this.terminal.write("\r\nconnection closed");
    };
};

WebShellClient.prototype.send = function (data) {
    this.connection.send(JSON.stringify({"command": data, "username": this.username}));
};
