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
import { Component, Input } from '@angular/core';
import { ICON_PATHS } from './icon.data';

/**
 * Renders a locally-bundled Material Symbols glyph as an inline SVG, so the
 * Admin UI needs no external icon font and works fully offline.
 *
 * It is a drop-in replacement for the former {@code <span class="material-icons">name</span>}
 * markup: the host element carries the {@code material-icons} class, so every
 * existing {@code .material-icons} style rule (sizing, color-by-action, hover)
 * keeps applying unchanged. The glyph scales to the inherited {@code font-size}
 * and inherits {@code color} via {@code fill: currentColor} (see styles.scss).
 *
 * Usage:
 *   <app-icon name="delete"></app-icon>
 *   <app-icon [name]="expanded ? 'expand_more' : 'chevron_right'"></app-icon>
 */
@Component({
    selector: 'app-icon',
    template:
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 -960 960 960"' +
        ' fill="currentColor" aria-hidden="true" focusable="false">' +
        '<path [attr.d]="path"></path></svg>',
    host: {'class': 'material-icons'}
})
export class KxIconComponent {

    path = '';
    private iconName = '';

    @Input()
    set name(value: string) {
        this.iconName = value;
        this.path = (value && ICON_PATHS[value]) || '';
    }

    get name(): string {
        return this.iconName;
    }
}
