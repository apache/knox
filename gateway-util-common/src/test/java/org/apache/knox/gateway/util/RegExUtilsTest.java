package org.apache.knox.gateway.util;

import org.junit.Assert;
import org.junit.Test;

import java.net.MalformedURLException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RegExUtilsTest {

  @Test
  public void testWhitelistMatching() {
    String whitelist = "^https?://.*example.com:8080/.*$;" +
        "^https?://.*example.com/.*$;" +
        "^https?://.*example2.com:\\d{0,9}/.*$;" +
        "^https://.*example3.com:\\d{0,9}/.*$;" +
        "^https?://localhost:\\d{0,9}/.*$;^/.*$";

    // match on explicit hostname/domain and port
    Assert.assertTrue("Failed to match whitelist", RegExUtils.checkWhitelist(whitelist,
        "http://host.example.com:8080/"));
    // match on non-required port
    Assert.assertTrue("Failed to match whitelist", RegExUtils.checkWhitelist(whitelist,
        "http://host.example.com/"));
    // match on required but any port
    Assert.assertTrue("Failed to match whitelist", RegExUtils.checkWhitelist(whitelist,
        "http://host.example2.com:1234/"));
    // fail on missing port
    assertFalse("Matched whitelist inappropriately", RegExUtils.checkWhitelist(whitelist,
        "http://host.example2.com/"));
    // fail on invalid port
    assertFalse("Matched whitelist inappropriately", RegExUtils.checkWhitelist(whitelist,
        "http://host.example.com:8081/"));
    // fail on alphanumeric port
    assertFalse("Matched whitelist inappropriately", RegExUtils.checkWhitelist(whitelist,
        "http://host.example.com:A080/"));
    // fail on invalid hostname/domain
    assertFalse("Matched whitelist inappropriately", RegExUtils.checkWhitelist(whitelist,
        "http://host.example.net:8080/"));
    // fail on required port
    assertFalse("Matched whitelist inappropriately", RegExUtils.checkWhitelist(whitelist,
        "http://host.example2.com/"));
    // fail on required https
    assertFalse("Matched whitelist inappropriately", RegExUtils.checkWhitelist(whitelist,
        "http://host.example3.com/"));
    // match on localhost and port
    Assert.assertTrue("Failed to match whitelist", RegExUtils.checkWhitelist(whitelist,
        "http://localhost:8080/"));
    // match on local/relative path
    Assert.assertTrue("Failed to match whitelist", RegExUtils.checkWhitelist(whitelist,
        "/local/resource/"));
  }

  @Test
  public void testWhitelistMatchingAgainstBaseURL() throws MalformedURLException {
    Assert.assertTrue("Failed to match whitelist",
            RegExUtils.checkBaseUrlAgainstWhitelist("^https?:\\/\\/(.*KNOX_GW_DOMAIN)(?::[0-9]+)?(?:\\/.*)?$",
                    "https://KNOX_GW_DOMAIN"));
    Assert.assertTrue("Failed to match whitelist",
            RegExUtils.checkBaseUrlAgainstWhitelist("^https?:\\/\\/(.*KNOX_GW_DOMAIN)(?::[0-9]+)?(?:\\/.*)?$",
                    "https://KNOX_GW_DOMAIN?a=1&b=2"));
    Assert.assertTrue("Failed to match whitelist",
            RegExUtils.checkBaseUrlAgainstWhitelist("^https?:\\/\\/(.*KNOX_GW_DOMAIN)(?::[0-9]+)?(?:\\/.*)?$",
                    "https://KNOX_GW_DOMAIN/path1/path2/path/3?a=1&b=2"));
    Assert.assertFalse("Inappropriately matched whitelist",
            RegExUtils.checkBaseUrlAgainstWhitelist("^https?:\\/\\/(.*KNOX_GW_DOMAIN)(?::[0-9]+)?(?:\\/.*)?$",
                    "https://google.com?https://KNOX_GW_DOMAIN"));
    Assert.assertFalse("Inappropriately matched whitelist",
            RegExUtils.checkBaseUrlAgainstWhitelist("^https?:\\/\\/(.*KNOX_GW_DOMAIN)(?::[0-9]+)?(?:\\/.*)?$",
                    "https://google.com/https://KNOX_GW_DOMAIN"));
  }

  @Test
  public void testMaliciousOriginalUrl() throws Exception {
    String whitelist = "^(?!.*([<>\"'`{}|\\\\^]|<script|%3cscript|javascript:|data:|alert\\(|onclick=))(^https?://.*example.com/.*)$";

    // make sure it is malicious and therefore does NOT match
    assertFalse(RegExUtils.checkWhitelist(whitelist, "https://example.com/path?param=%3e%3cscript%3e"));
    // make sure it matches because it is not malicious
    assertTrue(RegExUtils.checkWhitelist(whitelist, "https://example.com/path"));
  }

  @Test(expected = MalformedURLException.class)
  public void testMalformedOriginalUrl() throws MalformedURLException {
    RegExUtils.checkBaseUrlAgainstWhitelist(".*", "https://localhost:5003gateway/homepage/home/");
  }
} 