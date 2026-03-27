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
# Knox Authentication Theming System

This directory contains theme files for customizing the appearance of the Knox authentication login page.

## Overview

The Knox authentication page supports custom themes through CSS overrides. Themes allow operators to customize the login page appearance to match their organization's branding without modifying the core Knox CSS files.

## How It Works

1. **Base Styles**: The default Knox styles are defined in `styles/knox.css`
2. **Theme Overrides**: Theme CSS files in this directory override the default styles
3. **Theme Loading**: Themes are loaded via URL parameter or localStorage

## Using Themes

### Method 1: URL Parameter (Recommended for testing)

Add `?theme=THEME_NAME` to the login URL:

```
https://knox.example.com/gateway/knoxsso/knoxauth/login.html?theme=modern
```

This will:
- Load the theme immediately
- Save the theme preference to localStorage for future visits

### Method 2: localStorage (Automatic persistence)

Once a theme is loaded via URL parameter, it's automatically saved and will persist across browser sessions.

To clear the saved theme:
```javascript
localStorage.removeItem('knox-auth-theme');
```

### Method 3: Default Theme

To return to the default Knox theme, use:
```
https://knox.example.com/gateway/knoxsso/knoxauth/login.html?theme=default
```

## Available Themes

### Default Theme (no theme parameter)
The classic Knox login page with:
- Dark gray background (#292929)
- Semi-transparent dark form container
- Traditional input fields with bottom borders
- Standard blue buttons

### Modern Theme (`?theme=modern`)
A contemporary design with:
- Deep purple background (#120046)
- White container with rounded corners and shadow
- Pill-shaped input fields and buttons
- Gradient purple button (#3b2edb to #5555f9)
- Roboto font family
- Responsive design

## Directory Structure

```
styles/themes/
├── README.md                    # This file
├── modern/                      # Modern theme (complete theme in one directory)
│   ├── theme.css               # Theme CSS file
│   └── images/                 # Theme-specific images
│       ├── wm-data-explorer.png
│       ├── wm-data-explorer@2x.png
│       └── wm-data-explorer@3x.png
└── corporate/                   # Example custom theme
    ├── theme.css               # Theme CSS file
    └── images/                 # Theme-specific images
        └── logo.png
```

Each theme is completely self-contained in its own directory with CSS and assets isolated from other themes.

## Creating Custom Themes

### Step 1: Create Theme Directory Structure

Create a theme directory with CSS file and assets:
```bash
# Create theme directory
mkdir -p styles/themes/THEME_NAME/images

# Create theme CSS file
touch styles/themes/THEME_NAME/theme.css
```

For example: Create `corporate/` directory with `corporate/theme.css` and `corporate/images/` for assets.

**Note:** The CSS file must be named `theme.css` and located in the theme's directory.

### Step 2: Override Styles

Use `!important` to ensure your styles override the defaults:

```css
/* Example: Custom theme */
body.login {
  background: #your-color !important;
}

.login #signin-container {
  width: 400px !important;
  background: #ffffff;
  border-radius: 16px;
}

.login input[type="text"],
.login input[type="password"] {
  border: solid 1px #your-border-color !important;
  border-radius: 8px !important;
}

.login form .btn {
  background: #your-button-color !important;
  border-radius: 8px !important;
}
```

### Step 3: Test Your Theme

Access the login page with your theme parameter:
```
https://knox.example.com/gateway/knoxsso/knoxauth/login.html?theme=corporate
```

## Theme Development Tips

### 1. Start with Modern Theme

Copy `modern-theme.css` as a starting point and modify the colors and styles.

### 2. Key Selectors to Override

| Element | Selector | Description |
|---------|----------|-------------|
| Background | `body.login` | Page background color |
| Container | `.login #signin-container` | Main login form container |
| Logo Area | `.login .l-logo` | Header/logo section |
| Form Body | `.login form` | Form padding and layout |
| Fields Container | `.login .fields` | Input fields wrapper |
| Labels | `.login label` | Field labels (Username, Password) |
| Inputs | `.login input[type="text"]`, `.login input[type="password"]` | Text input fields |
| Button | `.login form .btn`, `.login form button[type="button"]` | Sign in button |
| Error Box | `.login #errorBox` | Error message container |

### 3. Use Browser DevTools

1. Open the login page with `?theme=modern`
2. Open browser DevTools (F12)
3. Inspect elements and experiment with CSS
4. Copy working styles to your theme file

### 4. Font Customization

To use custom fonts, add a `@import` at the top of your theme file:

```css
@import url('https://fonts.googleapis.com/css2?family=Your+Font:wght@400;600&display=swap');

body.login {
  font-family: 'Your Font', sans-serif;
}
```

### 5. Logo Customization

There are three ways to customize the login page logo:

#### Option A: CSS Content Override (Recommended)

Place your logo images in your theme's images directory and reference them in your theme CSS:

```css
/* corporate/theme.css */

.login .l-logo img {
  /* Reference theme-specific logo (relative to theme.css location) */
  content: url('images/logo.png');
  max-width: 260px;
  max-height: 50px;
}

/* Support for high-DPI/Retina displays */
@media only screen and (-webkit-min-device-pixel-ratio: 2),
       only screen and (min-resolution: 192dpi) {
  .login .l-logo img {
    content: url('images/logo@2x.png');
    width: 260px;
    height: 50px;
  }
}

@media only screen and (-webkit-min-device-pixel-ratio: 3),
       only screen and (min-resolution: 288dpi) {
  .login .l-logo img {
    content: url('images/logo@3x.png');
    width: 260px;
    height: 50px;
  }
}
```

**Directory structure:**
```
styles/themes/
└── corporate/
    ├── theme.css           (Theme CSS file)
    └── images/
        ├── logo.png        (1x - standard DPI)
        ├── logo@2x.png     (2x - Retina/high-DPI)
        └── logo@3x.png     (3x - ultra high-DPI)
```

This approach:
- Each theme is self-contained in one directory
- Keeps each theme's assets isolated
- Doesn't modify core Knox images
- Completely replaces the logo without modifying `login.html`
- Supports multiple themes simultaneously
- Image paths are relative to the theme.css file location

#### Option B: Background Image

Replace the image with a background (useful for SVG logos):

```css
/* corporate/theme.css */

.login .l-logo img {
  opacity: 0;
  height: 50px;
}

.login .l-logo {
  background: url('images/logo.svg') center no-repeat;
  background-size: contain;
  height: 108px;
}
```

#### Option C: Text Logo

Replace the image with styled text:

```css
.login .l-logo img {
  display: none;
}

.login .l-logo::before {
  content: 'Your Company Name';
  font-size: 24px;
  font-weight: 600;
  color: #1a2024;
}
```

**Best Practice:** Use Option A with multiple resolutions (@1x, @2x, @3x) for optimal display across all devices and screen densities.

### 6. Responsive Design

Always test on mobile devices. Add responsive breakpoints:

```css
@media (max-width: 480px) {
  .login #signin-container {
    width: 90% !important;
  }
}
```

## Theme Structure Reference

The login page structure (simplified):

```html
<body class="login">
  <section id="signin-container">
    <div class="l-logo">
      <img src="images/knox-logo.gif" alt="Knox logo">
    </div>
    <form>
      <fieldset>
        <div class="fields">
          <label>Username:</label>
          <input type="text" name="username">
          <label>Password:</label>
          <input type="password" name="password">
        </div>
        <span id="errorBox" class="help-inline">
          <span class="errorMsg"></span>
        </span>
        <button type="button" class="btn btn-primary btn-block" id="signIn">
          Sign In
        </button>
      </fieldset>
    </form>
  </section>
</body>
```

## Deployment

### Setting a Default Theme at Deployment Time

Knox provides a configuration file for admins to set the default theme without modifying code.

#### Option 1: Edit theme-config.js (Recommended)

Edit the `theme-config.js` file in the knoxauth application directory:

```javascript
// Default theme to use for this deployment
var KNOX_DEFAULT_THEME = 'modern';  // Change from 'default' to your theme

// Lock theme to prevent user overrides
var KNOX_THEME_LOCKED = false;  // Set to true to enforce theme
```

**Location:** `gateway-applications/src/main/resources/applications/knoxauth/app/theme-config.js`

**Examples:**

Use modern theme by default:
```javascript
var KNOX_DEFAULT_THEME = 'modern';
var KNOX_THEME_LOCKED = false;
```

Lock to modern theme (users cannot change it):
```javascript
var KNOX_DEFAULT_THEME = 'modern';
var KNOX_THEME_LOCKED = true;
```

Use your corporate theme:
```javascript
var KNOX_DEFAULT_THEME = 'corporate';
var KNOX_THEME_LOCKED = false;
```

#### Theme Selection Priority

When `KNOX_THEME_LOCKED = false` (default):
1. **URL parameter** (`?theme=modern`) - Highest priority
2. **localStorage** (user's saved preference)
3. **KNOX_DEFAULT_THEME** (deployment default)

When `KNOX_THEME_LOCKED = true`:
1. **KNOX_DEFAULT_THEME** only (URL parameter and localStorage ignored)

### For Organizations

#### Deployment Scenarios

**Scenario 1: Offer modern theme as default, allow users to switch**
```javascript
var KNOX_DEFAULT_THEME = 'modern';
var KNOX_THEME_LOCKED = false;
```
- All users see modern theme by default
- Users can use `?theme=default` to switch to classic Knox theme
- User preference persists via localStorage

**Scenario 2: Enforce corporate branding, no user customization**
```javascript
var KNOX_DEFAULT_THEME = 'corporate';
var KNOX_THEME_LOCKED = true;
```
- All users see corporate theme
- URL parameters ignored
- localStorage ignored
- Consistent branding across all users

**Scenario 3: Classic Knox theme (default behavior)**
```javascript
var KNOX_DEFAULT_THEME = 'default';
var KNOX_THEME_LOCKED = false;
```
- Uses classic Knox styling
- Users can opt-in to modern theme with `?theme=modern`

### Alternative Deployment Methods

If you prefer not to use theme-config.js:

**Option A**: Web server redirect/rewrite rule
```apache
# Apache example - add ?theme=modern automatically
RewriteRule ^(.*/knoxauth/login\.html)$ $1?theme=modern [QSA,L]
```

**Option B**: Create a custom landing page
```html
<!-- index.html -->
<script>
  window.location.href = 'login.html?theme=modern';
</script>
```

**Option C**: Modify login.html directly (not recommended)
Change the default in login.html's theme loader script.

## Browser Compatibility

Themes are tested and supported on:
- Chrome 90+
- Firefox 88+
- Safari 14+
- Edge 90+

Modern CSS features used:
- CSS Custom Properties (CSS Variables)
- Flexbox
- Border-radius
- Linear gradients
- Box shadows
- Media queries

## Security Considerations

1. **XSS Protection**: Theme names are not executed as code, only used to construct file paths
2. **Path Traversal**: Theme loader only loads files from `styles/themes/` directory
3. **Content Security Policy**: Ensure CSP allows loading external fonts if using Google Fonts

## Troubleshooting

### Theme Not Loading

1. Check browser console (F12) for CSS loading errors
2. Verify theme file exists: `styles/themes/THEME_NAME-theme.css`
3. Clear browser cache (Ctrl+Shift+R or Cmd+Shift+R)
4. Check localStorage: `localStorage.getItem('knox-auth-theme')`

### Styles Not Applying

1. Ensure you're using `!important` in your theme CSS
2. Check CSS selector specificity
3. Verify theme CSS loads after knox.css (check DevTools Network tab)

### localStorage Not Working

Some browsers block localStorage in private/incognito mode. The theme system falls back to URL parameter only in this case.

## Examples

### Example 1: Dark Theme

```css
/* dark-theme.css */
body.login {
  background: #1a1a1a !important;
}

.login #signin-container {
  background: #2d2d2d;
  border-radius: 8px;
}

.login input {
  background: #3d3d3d !important;
  border: 1px solid #555 !important;
  color: #fff !important;
}

.login label {
  color: #ccc !important;
}

.login form .btn {
  background: #007bff !important;
}
```

### Example 2: Corporate Theme with Brand Colors

```css
/* corporate-theme.css */
@import url('https://fonts.googleapis.com/css2?family=Open+Sans:wght@400;600&display=swap');

body.login {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%) !important;
  font-family: 'Open Sans', sans-serif;
}

.login #signin-container {
  width: 420px !important;
  background: #ffffff;
  border-radius: 12px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.3);
}

.login form .btn {
  background: #667eea !important;
  border-radius: 6px !important;
}
```

## Support

For issues or questions about theming:
1. Check this README.md
2. Review `modern-theme.css` as a reference implementation
3. Use browser DevTools to inspect and debug styles
4. Consult Knox documentation at https://knox.apache.org

## License

These theme files are part of Apache Knox and are licensed under the Apache License 2.0.
