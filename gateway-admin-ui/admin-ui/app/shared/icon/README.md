<!--
  Licensed to the Apache Software Foundation (ASF) under one or more
  contributor license agreements.  See the NOTICE file distributed with
  this work for additional information regarding copyright ownership.
  The ASF licenses this file to You under the Apache License, Version 2.0
  (the "License"); you may not use this file except in compliance with
  the License.  You may obtain a copy of the License at
      http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
-->
# Bundled icons

The Knox UIs bundle their icons locally as inline SVGs so the UI renders fully
offline — in air-gapped networks there is **no** external font/CSS fetch to
`fonts.googleapis.com` / `fonts.gstatic.com` at build or runtime. This was the
behavior originally established by KNOX-1731 and regressed by the Angular 21 UI
upgrade (KNOX-3234).

## Icon set

- **Source:** Google [Material Symbols Outlined](https://github.com/google/material-design-icons)
- **Style:** Outlined, weight 400, grade 0, optical size 24
- **License:** Apache License, Version 2.0 (see the repo-root `NOTICE`)
- **Coordinate grid:** `viewBox="0 -960 960 960"`, `fill="currentColor"`, no hardcoded colors

## How it works

- **admin-ui** (this module): `icon.data.ts` holds the SVG `path` `d` strings;
  `icon.component.ts` (`<kx-icon name="...">`) renders them inside the shared
  `viewBox`. The host carries the `material-icons` class so the existing
  `styles.scss` theming (color-by-action, hover, sizing) keeps matching.
  The inline-edit hover pencil is `assets/icons/edit.svg`, applied as a CSS mask.
- **mat-icon modules** (home, token-generation, token-management): each has an
  `app/icons/icon-registry.provider.ts` that registers the inline SVGs with
  Angular Material's `MatIconRegistry` via `addSvgIconLiteral`; templates use
  `<mat-icon svgIcon="...">`.

## Updating / adding an icon

Copy the official SVG `path` data for the glyph (same Material Symbols Outlined
settings as above) into `icon.data.ts` (admin-ui) or the relevant module's
`icon-registry.provider.ts`. Do not reintroduce `<link>` tags to Google Fonts
or any icon-font binary.
