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
import {Component, OnInit, ViewChild, AfterViewInit, HostListener} from '@angular/core';
import { NgTerminal } from 'ng-terminal';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent implements OnInit, AfterViewInit {
  @ViewChild('term', {static: false}) child: NgTerminal;
  private websocket: WebSocket;

  constructor() { }

  ngOnInit() {}

  ngAfterViewInit() {
    const terminal = this.child.underlying;
    // todo: can add other options to customize xterm
    terminal.options.convertEol = true;
    terminal.options.letterSpacing = 0;
    terminal.options.fontFamily = 'Courier New';

    /* check font
    const fontFamily = terminal.getOption("fontFamily");
    console.log(fontFamily);
    */

    let endpoint = 'wss://'+ location.hostname + ':' + location.port + '/'+
        location.pathname.split('/')[1] + '/webshell';
    console.log(endpoint);
    this.websocket = new WebSocket(endpoint);
    this.websocket.onmessage = function(event){
      terminal.write(event.data);
    }
    this.websocket.onclose = function(event){
      terminal.write("\r\nConnection closed");
    }

    terminal.onData((userInput) => {
      // send userInput to backend server
      this.websocket.send(JSON.stringify({userInput:userInput}));
    })
  }
}
