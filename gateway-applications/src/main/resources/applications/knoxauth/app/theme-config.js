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

/**
 * Knox Authentication Theme Configuration
 *
 * This file configures the default theme for the Knox authentication page.
 *
 * DEPLOYMENT CONFIGURATION:
 *
 * To set a default theme for your deployment, change the KNOX_DEFAULT_THEME value:
 *
 *   'default' - Use the classic Knox theme (dark gray background)
 *   'modern'  - Use the modern theme (purple background, white container)
 *   'custom'  - Use your custom theme (must exist in styles/themes/custom/)
 *
 * Theme selection priority:
 *   1. URL parameter (?theme=modern) - Highest priority
 *   2. User's saved preference (localStorage)
 *   3. KNOX_DEFAULT_THEME (set below) - Deployment default
 *
 * EXAMPLES:
 *
 * Use modern theme by default:
 *   var KNOX_DEFAULT_THEME = 'modern';
 *
 * Use classic Knox theme:
 *   var KNOX_DEFAULT_THEME = 'default';
 *
 * Use your corporate theme:
 *   var KNOX_DEFAULT_THEME = 'corporate';
 *
 * LOCKING TO A SINGLE THEME:
 *
 * To force a specific theme and prevent users from changing it,
 * set KNOX_THEME_LOCKED to true:
 *   var KNOX_DEFAULT_THEME = 'modern';
 *   var KNOX_THEME_LOCKED = true;
 *
 * When locked, URL parameters and localStorage are ignored.
 */

// Default theme to use for this deployment
var KNOX_DEFAULT_THEME = 'default';

// Lock theme to prevent user overrides (true = locked, false = allow overrides)
var KNOX_THEME_LOCKED = false;
