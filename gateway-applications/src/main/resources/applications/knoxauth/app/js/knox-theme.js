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
 * Loads Knox authentication theme CSS on login and logout pages.
 * Include theme-config.js before this script in the document head.
 */
(function() {
	// Theme names are attacker-influenced (URL parameter and localStorage), so
	// every candidate is validated before it is stored or used to build a URL.
	var THEME_PATTERN = /^[a-zA-Z0-9_-]{1,64}$/;

	function sanitize(candidate) {
		return (candidate && THEME_PATTERN.test(candidate)) ? candidate : null;
	}

	var theme;
	var themeIsPersisted = false;

	if (typeof KNOX_THEME_LOCKED !== 'undefined' && KNOX_THEME_LOCKED === true) {
		theme = sanitize(KNOX_DEFAULT_THEME) || 'default';
	} else {
		var urlParams = new URLSearchParams(window.location.search);
		var requested = sanitize(urlParams.get('theme'));

		if (requested) {
			try {
				localStorage.setItem('knox-auth-theme', requested);
				themeIsPersisted = true;
			} catch (e) {
				// LocalStorage may be disabled, continue without saving
			}
			theme = requested;
		} else {
			try {
				var saved = localStorage.getItem('knox-auth-theme');
				theme = sanitize(saved);
				if (saved && !theme) {
					localStorage.removeItem('knox-auth-theme');
				}
				themeIsPersisted = theme !== null;
			} catch (e) {
				theme = null;
			}
			theme = theme || sanitize(KNOX_DEFAULT_THEME) || 'default';
		}
	}

	if (theme !== 'default') {
		var link = document.createElement('link');
		link.rel = 'stylesheet';
		link.type = 'text/css';
		link.id = 'knox-theme';
		if (themeIsPersisted) {
			link.onerror = function() {
				try {
					localStorage.removeItem('knox-auth-theme');
				} catch (e) {
					// LocalStorage may be disabled, nothing to clean up
				}
			};
		}
		link.href = 'styles/themes/' + theme + '/theme.css';
		document.head.appendChild(link);
	}
})();
