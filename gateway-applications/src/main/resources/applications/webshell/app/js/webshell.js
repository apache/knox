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

function WebShellClient() {
};


WebShellClient.prototype.connect = function (callbackFuncs) {
    var endpoint = 'wss://localhost:8443/gateway/homepage/webshell/webshellws';
    console.log('connecting websocket endpoint:' + endpoint);

    if (window.WebSocket) {
        // When new WebSocket(url) is created, it starts connecting immediately
        this._connection = new WebSocket(endpoint);
    }else {
        callbackFuncs.onError('WebSocket Not Supported');
        return;
    }

    this._connection.onopen = function () {
        console.log('WebSocket connection established');
        callbackFuncs.onOpen();
    };

    this._connection.onmessage = function (event) {
        var data = event.data.toString();
        callbackFuncs.onData(data);
    };

    this._connection.onerror = function (event) {
        var error = event.data.toString();
        callbackFuncs.onError(error);
    };


    this._connection.onclose = function (event) {
        callbackFuncs.onClose();
    };
};

WebShellClient.prototype.send = function (data) {
    this._connection.send(JSON.stringify(data));
};

WebShellClient.prototype.sendInitData = function (options) {
    console.log('send initializing data through websocket'+JSON.stringify(options));
    this._connection.send(JSON.stringify(options));
}

WebShellClient.prototype.sendClientData = function (data) {
    this._connection.send(JSON.stringify({"operation": "command", "command": data}))
}

var client = new WebShellClient();
