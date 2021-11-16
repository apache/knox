/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import { Component, OnInit, ViewChild, AfterViewInit } from '@angular/core';
import { NgTerminal } from 'ng-terminal';
import { FunctionsUsingCSI } from 'ng-terminal';
import { webSocket, WebSocketSubject } from 'rxjs/webSocket';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent implements OnInit, AfterViewInit {
  webSocketSubject:WebSocketSubject<any>;
  @ViewChild('term', {static: false}) child: NgTerminal;

  constructor() { }

  ngOnInit() {}

  ngAfterViewInit() {
    this.child.underlying.options.fontSize = 20;
    this.child.underlying.options.convertEol = true;
    let endpoint = 'wss://'+ location.hostname + ':' + location.port + '/'+
        location.pathname.split('/')[1] + '/webshell';
    console.log(endpoint);
    this.webSocketSubject = webSocket({
      url: endpoint,
      deserializer: msg => msg // avoid default behavior to call JSON.parse(msg)
    });
    this.webSocketSubject.subscribe(
      // Called whenever there is a message from the server
      msg => {
        console.log('received message')
        console.log(msg);
        this.child.write(msg.data);
      },
      // Called if WebSocket API signals some kind of error
      err => {
        console.log(err);
      },
      // Called when connection is closed (for whatever reason)
      () => {
        console.log('connection closed');
        this.child.write('connection closed');
      }
    );

    this.child.keyEventInput.subscribe(e => {
      console.log('keyboard event:' + e.domEvent.key);
      let key = e.domEvent.key;
      if (key === 'Enter'){
        key = '\n';
      } else if (key === 'Backspace') {
        key = '\b';
      } else if (key === 'Tab') {
        key = '\t';
      } else if (key === 'Escape'){
        key = '\x1b';
      }
      // send key to backend server
      this.webSocketSubject.next({command:key});
    })
  }
}
