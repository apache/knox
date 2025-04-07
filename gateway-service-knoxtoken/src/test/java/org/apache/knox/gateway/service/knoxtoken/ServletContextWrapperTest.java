/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.service.knoxtoken;

import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.ServletContext;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ServletContextWrapperTest {

    private ServletContext mockDelegate;
    private ServletContextWrapper wrapper;

    @Before
    public void setUp() {
        mockDelegate = EasyMock.createMock(ServletContext.class);
        wrapper = new ServletContextWrapper(mockDelegate);
    }

    @Test
    public void testGetInitParameterDefaultParam() {
        wrapper.setInitParameter("defaultParam", "defaultValue");
        assertEquals("defaultValue", wrapper.getInitParameter("defaultParam"));
    }

    @Test
    public void testGetInitParameterDelegateParam() {
        EasyMock.expect(mockDelegate.getInitParameter("delegateParam")).andReturn("delegateValue");
        EasyMock.replay(mockDelegate);

        assertEquals("delegateValue", wrapper.getInitParameter("delegateParam"));
        EasyMock.verify(mockDelegate);
    }

    @Test
    public void testGetInitParameterOverrideDefaultParam() {
        wrapper.setInitParameter("defaultParam", "defaultValue");
        EasyMock.expect(mockDelegate.getInitParameter("delegateParam")).andReturn("delegateValue");
        EasyMock.replay(mockDelegate);

        assertEquals("delegateValue", wrapper.getInitParameter("delegateParam"));
        EasyMock.verify(mockDelegate);
    }

    @Test
    public void testGetInitParameterNames() {
        wrapper.setInitParameter("defaultParam", "defaultValue");
        EasyMock.expect(mockDelegate.getInitParameterNames()).andReturn(new Enumeration<String>() {
            private final String[] elements = {"delegateParam"};
            private int index;

            @Override
            public boolean hasMoreElements() {
                return index < elements.length;
            }

            @Override
            public String nextElement() {
                return elements[index++];
            }
        });
        EasyMock.replay(mockDelegate);

        Enumeration<String> paramNames = wrapper.getInitParameterNames();
        Map<String, Boolean> paramMap = new HashMap<>();
        while (paramNames.hasMoreElements()) {
            paramMap.put(paramNames.nextElement(), true);
        }

        assertTrue(paramMap.containsKey("defaultParam"));
        assertTrue(paramMap.containsKey("delegateParam"));
        EasyMock.verify(mockDelegate);
    }

    @Test
    public void testGetInitParameterNamesWithDupes() {
        wrapper.setInitParameter("testParam", "testValue");
        EasyMock.expect(mockDelegate.getInitParameterNames()).andReturn(new Enumeration<String>() {
            private final String[] elements = {"delegateParam", "testParam"};
            private int index;

            @Override
            public boolean hasMoreElements() {
                return index < elements.length;
            }

            @Override
            public String nextElement() {
                return elements[index++];
            }
        });
        EasyMock.replay(mockDelegate);

        Enumeration<String> paramNames = wrapper.getInitParameterNames();
        Map<String, Boolean> paramMap = new HashMap<>();
        while (paramNames.hasMoreElements()) {
            paramMap.put(paramNames.nextElement(), true);
        }

        assertTrue(paramMap.containsKey("testParam"));
        assertTrue(paramMap.containsKey("delegateParam"));
        assertEquals(2, paramMap.size());
        EasyMock.verify(mockDelegate);
    }

    @Test
    public void testSetInitParameter() {
        assertTrue(wrapper.setInitParameter("newParam", "newValue"));
        assertEquals("newValue", wrapper.getInitParameter("newParam"));
    }
}