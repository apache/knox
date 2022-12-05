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
/* tslint:disable:no-unused-variable */
import {AppComponent} from './app.component';

import {TestBed} from '@angular/core/testing';

import {By} from '@angular/platform-browser';

// SPECS

// Delete this
describe('Smoke test', () => {
    it('should run a passing test', () => {
        expect(true).toEqual(true, 'should pass');
    });
});

describe('AppComponent with TCB', function () {
    beforeEach(() => {
        TestBed.configureTestingModule({declarations: [AppComponent]});
    });

    it('should instantiate component', () => {
        let fixture = TestBed.createComponent(AppComponent);
        expect(fixture.componentInstance instanceof AppComponent).toBe(true, 'should create AppComponent');
    });

    it('should have expected <h1> text', () => {
        let fixture = TestBed.createComponent(AppComponent);
        fixture.detectChanges();

        let h1 = fixture.debugElement.query(el => el.name === 'h1').nativeElement;  // it works

        h1 = fixture.debugElement.query(By.css('h1')).nativeElement;            // preferred

        expect(h1.innerText).toMatch(/angular 2 app/i, '<h1> should say something about "Angular 2 App"');
    });
});
