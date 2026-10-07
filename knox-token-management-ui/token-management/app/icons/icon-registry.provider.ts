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

/* eslint-disable max-len -- SVG path 'd' data cannot be meaningfully line-wrapped */
import { EnvironmentProviders, inject, provideEnvironmentInitializer } from '@angular/core';
import { MatIconRegistry } from '@angular/material/icon';
import { DomSanitizer } from '@angular/platform-browser';

/*
 * Inline SVG icons bundled locally so the UI renders fully offline (air-gapped
 * networks). Sourced from the Google Material Symbols Outlined set, which is
 * licensed under the Apache License, Version 2.0.
 * See github.com/google/material-design-icons. Each glyph uses the Material
 * Symbols coordinate grid (viewBox="0 -960 960 960") and fill="currentColor"
 * so it inherits the surrounding text color.
 */
const ICONS: { [name: string]: string } = {
  refresh:
    '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 -960 960 960" fill="currentColor"><path d="M480-160q-134 0-227-93t-93-227q0-134 93-227t227-93q69 0 132 28.5T720-690v-110h80v280H520v-80h168q-32-56-87.5-88T480-720q-100 0-170 70t-70 170q0 100 70 170t170 70q77 0 139-44t87-116h84q-28 106-114 173t-196 67Z"/></svg>'
};

/**
 * Registers every locally-bundled SVG icon with Angular Material's
 * {@link MatIconRegistry} using {@code addSvgIconLiteral}, so templates can use
 * {@code <mat-icon svgIcon="name">} with no runtime HTTP fetch.
 */
export function provideIconRegistry(): EnvironmentProviders {
  return provideEnvironmentInitializer(() => {
    const registry = inject(MatIconRegistry);
    const sanitizer = inject(DomSanitizer);
    Object.keys(ICONS).forEach(name => {
      registry.addSvgIconLiteral(name, sanitizer.bypassSecurityTrustHtml(ICONS[name]));
    });
  });
}
