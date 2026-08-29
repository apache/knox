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
package org.apache.knox.gateway.openapi.ui;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

import org.junit.Test;
import org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class OAuth2RedirectScriptTest {

  private static final String[] AUTHORIZATION_CODE_FLOWS = {
      "accessCode", "authorizationCode", "authorization_code"
  };
  private static final String SCRIPT = loadScript();

  @Test
  public void rejectsAuthorizationCodeWhenStateDoesNotMatch() throws Exception {
    for (String flow : AUTHORIZATION_CODE_FLOWS) {
      ScriptEngine engine = newEngine("?code=foreign-code&state=unexpected-state", "", "expected-state", flow, null);

      run(engine);

      assertNull(engine.get("callbackPayload"));
      assertEquals(1, numberResult(engine, "errPayloads.length"));
      assertEquals("error", stringResult(engine, "errPayloads[0].level"));
      assertEquals("[Authorization failed]: returned state does not match the original request",
          stringResult(engine, "errPayloads[0].message"));
      assertNull(engine.eval("window.opener.swaggerUIRedirectOauth2.auth.code"));
      assertEquals("expected-state", stringResult(engine, "window.opener.swaggerUIRedirectOauth2.state"));
      assertEquals(1, numberResult(engine, "closeCount"));
    }
  }

  @Test
  public void acceptsAuthorizationCodeWhenStateMatches() throws Exception {
    for (String flow : AUTHORIZATION_CODE_FLOWS) {
      ScriptEngine engine = newEngine("?code=expected-code&state=expected-state", "", "expected-state", flow, null);

      run(engine);

      assertNotNull(engine.get("callbackPayload"));
      assertEquals("expected-code", stringResult(engine, "window.opener.swaggerUIRedirectOauth2.auth.code"));
      assertEquals(Boolean.FALSE, engine.eval("window.opener.swaggerUIRedirectOauth2.hasOwnProperty('state')"));
      assertEquals(0, numberResult(engine, "errPayloads.length"));
      assertEquals(1, numberResult(engine, "closeCount"));
    }
  }

  @Test
  public void rejectsMismatchedStateWhenAuthorizationCodeAlreadyExists() throws Exception {
    ScriptEngine engine = newEngine("?code=foreign-code&state=unexpected-state", "", "expected-state",
        "authorizationCode", "stale-code");

    run(engine);

    assertNull(engine.get("callbackPayload"));
    assertEquals(1, numberResult(engine, "errPayloads.length"));
    assertEquals("error", stringResult(engine, "errPayloads[0].level"));
    assertEquals("stale-code", stringResult(engine, "window.opener.swaggerUIRedirectOauth2.auth.code"));
    assertEquals("expected-state", stringResult(engine, "window.opener.swaggerUIRedirectOauth2.state"));
    assertEquals(1, numberResult(engine, "closeCount"));
  }

  @Test
  public void preservesImplicitFlowCallbackBehavior() throws Exception {
    ScriptEngine engine = newEngine("", "#access_token=token-value&state=unexpected-state", "expected-state",
        "implicit", null);

    run(engine);

    assertNotNull(engine.get("callbackPayload"));
    assertEquals(Boolean.FALSE, engine.eval("callbackPayload.isValid"));
    assertEquals("token-value", stringResult(engine, "callbackPayload.token.access_token"));
    assertEquals(0, numberResult(engine, "errPayloads.length"));
    assertEquals(1, numberResult(engine, "closeCount"));
  }

  private static ScriptEngine newEngine(String search, String hash, String state, String flow, String code)
      throws ScriptException {
    ScriptEngine engine = new NashornScriptEngineFactory().getScriptEngine();
    engine.put("searchValue", search);
    engine.put("hashValue", hash);
    engine.put("expectedState", state);
    engine.put("flowValue", flow);
    engine.put("initialCode", code);
    engine.eval(
        "var callbackPayload = null;\n"
            + "var errPayloads = [];\n"
            + "var closeCount = 0;\n"
            + "var document = { readyState: 'loading', addEventListener: function() {} };\n"
            + "var location = { search: searchValue, hash: hashValue };\n"
            + "var window = {\n"
            + "  location: location,\n"
            + "  opener: {\n"
            + "    swaggerUIRedirectOauth2: {\n"
            + "      state: expectedState,\n"
            + "      redirectUrl: 'https://gateway.example.test/oauth2-redirect.html',\n"
            + "      auth: {\n"
            + "        code: initialCode,\n"
            + "        name: 'oauth2',\n"
            + "        schema: { get: function(key) { return key === 'flow' ? flowValue : null; } }\n"
            + "      },\n"
            + "      errCb: function(payload) { errPayloads.push(payload); },\n"
            + "      callback: function(payload) { callbackPayload = payload; }\n"
            + "    }\n"
            + "  },\n"
            + "  close: function() { closeCount++; },\n"
            + "  addEventListener: function() {}\n"
            + "};\n");
    engine.eval(SCRIPT);
    return engine;
  }

  private static void run(ScriptEngine engine) throws Exception {
    ((Invocable) engine).invokeFunction("run");
  }

  private static int numberResult(ScriptEngine engine, String expression) throws ScriptException {
    return ((Number) engine.eval(expression)).intValue();
  }

  private static String stringResult(ScriptEngine engine, String expression) throws ScriptException {
    return (String) engine.eval(expression);
  }

  private static String loadScript() {
    try (InputStream input = OAuth2RedirectScriptTest.class.getResourceAsStream("/swagger/oauth2-redirect.js")) {
      if (input == null) {
        throw new IllegalStateException("Unable to load swagger/oauth2-redirect.js");
      }

      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("Unable to read swagger/oauth2-redirect.js", e);
    }
  }
}
