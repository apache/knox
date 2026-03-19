# Knox Authentication Theme - Deployment Guide

This guide explains how administrators can configure the default theme for their Knox deployment.

## Quick Start

### Set Default Theme

Edit `theme-config.js` in the knoxauth application directory:

```bash
# Navigate to Knox installation
cd $KNOX_HOME/data/applications/knoxauth/app/

# Edit theme configuration
vi theme-config.js
```

Change the default theme:

```javascript
var KNOX_DEFAULT_THEME = 'modern';  // or 'default', 'corporate', etc.
var KNOX_THEME_LOCKED = false;      // false = users can override, true = enforce
```

Restart Knox gateway:

```bash
$KNOX_HOME/bin/gateway.sh stop
$KNOX_HOME/bin/gateway.sh start
```

That's it! Users will now see your configured theme.

## Configuration Options

### KNOX_DEFAULT_THEME

Sets the theme that loads by default when users access the login page.

**Values:**
- `'default'` - Classic Knox theme (dark gray background)
- `'modern'` - Modern theme (purple background, white container, pill-shaped inputs)
- `'custom'` - Any custom theme name (must exist in `styles/themes/custom/`)

**Default:** `'default'`

### KNOX_THEME_LOCKED

Controls whether users can override the theme.

**Values:**
- `false` - Users can use `?theme=` URL parameter to change theme
- `true` - Theme is enforced, users cannot change it

**Default:** `false`

## Common Deployment Scenarios

### Scenario 1: Modern Theme as Default, Users Can Switch

**Use Case:** Provide modern UI by default, but allow users to use classic theme if preferred.

```javascript
var KNOX_DEFAULT_THEME = 'modern';
var KNOX_THEME_LOCKED = false;
```

**Result:**
- All users see modern theme on first visit
- Users can visit `login.html?theme=default` to switch to classic Knox
- User preference persists via browser localStorage

---

### Scenario 2: Enforce Corporate Branding

**Use Case:** Require all users to see corporate-branded login page.

```javascript
var KNOX_DEFAULT_THEME = 'corporate';
var KNOX_THEME_LOCKED = true;
```

**Prerequisites:**
1. Create custom theme at `styles/themes/corporate/`
2. Add `corporate/theme.css` with branding styles
3. Add `corporate/images/` with company logo

**Result:**
- All users see corporate theme
- URL parameters ignored (cannot use `?theme=`)
- localStorage ignored (cannot save preference)
- Consistent experience for all users

---

### Scenario 3: Classic Knox (No Change)

**Use Case:** Keep existing Knox appearance (default behavior).

```javascript
var KNOX_DEFAULT_THEME = 'default';
var KNOX_THEME_LOCKED = false;
```

**Result:**
- Classic Knox theme for all users
- Users can opt-in to modern theme via `?theme=modern`

---

### Scenario 4: Per-Topology Themes

**Use Case:** Different themes for different Knox topologies/environments.

**Production topology:**
```javascript
var KNOX_DEFAULT_THEME = 'corporate';
var KNOX_THEME_LOCKED = true;
```

**Development topology:**
```javascript
var KNOX_DEFAULT_THEME = 'modern';
var KNOX_THEME_LOCKED = false;
```

Deploy different `theme-config.js` files to each environment.

---

## Deployment Methods

### Method 1: Edit Configuration File (Recommended)

**Pros:**
- Simple, one-file edit
- No code changes required
- Easy to automate via config management tools

**Steps:**
1. Build Knox with theme support
2. Deploy Knox
3. Edit `$KNOX_HOME/data/applications/knoxauth/app/theme-config.js`
4. Restart gateway

**Automation example (Ansible):**
```yaml
- name: Configure Knox theme
  copy:
    dest: "{{ knox_home }}/data/applications/knoxauth/app/theme-config.js"
    content: |
      var KNOX_DEFAULT_THEME = '{{ knox_theme }}';
      var KNOX_THEME_LOCKED = {{ knox_theme_locked }};
```

---

### Method 2: Build-Time Configuration

**Pros:**
- Configuration baked into build
- No post-deployment changes needed

**Steps:**
1. Edit `theme-config.js` before building Knox
2. Build Knox: `mvn clean install`
3. Deploy Knox distribution

**Best for:**
- Immutable infrastructure
- Container deployments
- Organizations with strict change control

---

### Method 3: Environment Variable (Advanced)

If you want to configure themes via environment variables, modify `theme-config.js`:

```javascript
// Read from environment or use default
var KNOX_DEFAULT_THEME = (typeof KNOX_THEME_ENV !== 'undefined')
    ? KNOX_THEME_ENV
    : 'default';
```

Then inject via server configuration or startup script.

---

## Testing Your Configuration

### Test Default Theme

```bash
# Access login page without parameters
curl -I https://knox.example.com/gateway/knoxsso/knoxauth/login.html

# Check the HTML source includes your theme
curl https://knox.example.com/gateway/knoxsso/knoxauth/login.html | grep theme-config.js
```

### Test Theme Lock

If `KNOX_THEME_LOCKED = true`, verify URL parameters are ignored:

```bash
# Both should show the same locked theme
https://knox.example.com/gateway/knoxsso/knoxauth/login.html
https://knox.example.com/gateway/knoxsso/knoxauth/login.html?theme=modern
```

---

## Troubleshooting

### Theme Not Loading

**Problem:** Login page shows default Knox theme instead of configured theme.

**Solutions:**
1. Verify theme exists: Check `styles/themes/THEME_NAME/theme.css` exists
2. Check browser console (F12) for CSS loading errors
3. Clear browser cache: Ctrl+Shift+R (Windows) or Cmd+Shift+R (Mac)
4. Verify `theme-config.js` is loaded: View page source and check script tag

### Theme Lock Not Working

**Problem:** Users can still override theme with URL parameters.

**Solutions:**
1. Verify `KNOX_THEME_LOCKED = true` (not `'true'` string)
2. Clear browser localStorage: `localStorage.clear()` in browser console
3. Hard refresh: Ctrl+Shift+R

### Users See Different Themes

**Problem:** Some users see modern theme, others see classic.

**Cause:** Users have different saved preferences in localStorage.

**Solutions:**
1. Set `KNOX_THEME_LOCKED = true` to enforce consistent theme
2. Ask users to clear localStorage or visit `login.html?theme=YOUR_THEME`
3. Deploy script to clear localStorage:
   ```javascript
   localStorage.removeItem('knox-auth-theme');
   ```

---

## Configuration Management Examples

### Ansible

```yaml
- name: Deploy Knox theme configuration
  copy:
    dest: "{{ knox_home }}/data/applications/knoxauth/app/theme-config.js"
    content: |
      var KNOX_DEFAULT_THEME = '{{ knox_default_theme | default("default") }}';
      var KNOX_THEME_LOCKED = {{ knox_theme_locked | default(false) | lower }};
    owner: knox
    group: knox
    mode: '0644'
  notify: restart knox gateway
```

### Docker

```dockerfile
# In Dockerfile
COPY theme-config.js /knox/data/applications/knoxauth/app/theme-config.js
```

Or with environment variable:

```bash
docker run -e KNOX_THEME=modern knox-gateway
```

### Kubernetes ConfigMap

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: knox-theme-config
data:
  theme-config.js: |
    var KNOX_DEFAULT_THEME = 'modern';
    var KNOX_THEME_LOCKED = true;
---
apiVersion: v1
kind: Pod
spec:
  volumes:
    - name: theme-config
      configMap:
        name: knox-theme-config
  containers:
    - name: knox
      volumeMounts:
        - name: theme-config
          mountPath: /knox/data/applications/knoxauth/app/theme-config.js
          subPath: theme-config.js
```

---

## Security Considerations

### Theme Lock for Compliance

Organizations with branding requirements or compliance needs should use `KNOX_THEME_LOCKED = true` to ensure consistent user experience.

### Theme Name Validation

The theme loader only loads files from `styles/themes/THEME_NAME/theme.css`. Path traversal attacks (e.g., `?theme=../../etc/passwd`) are prevented by the URL structure.

### Content Security Policy

If you have strict CSP, ensure it allows:
- Loading CSS from same origin
- Loading fonts from Google Fonts (if using modern theme)

Example CSP:
```
Content-Security-Policy: style-src 'self' https://fonts.googleapis.com;
```

---

## Maintenance

### Updating Themes

To update a theme after deployment:

1. Update theme files in `styles/themes/THEME_NAME/`
2. Clear browser cache or use cache-busting:
   ```html
   <link href="styles/themes/modern/theme.css?v=2">
   ```

### Adding New Themes

1. Create theme directory: `styles/themes/newtheme/`
2. Add `newtheme/theme.css`
3. Add `newtheme/images/` (optional)
4. Update `theme-config.js` to use new theme
5. Restart gateway

### Removing Themes

1. Delete theme directory: `rm -rf styles/themes/oldtheme/`
2. If it was the default, update `theme-config.js`

---

## Support

For issues or questions:
1. Check `styles/themes/README.md` for theme development documentation
2. Review browser console (F12) for JavaScript errors
3. Check Knox gateway logs for application errors
4. Consult Knox documentation at https://knox.apache.org
