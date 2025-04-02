/*
 * Copyright 2000-2025 Vaadin Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.vaadin.integration.maven;


import org.apache.maven.plugin.testing.AbstractMojoTestCase;

public class AbstractThemeMojoTest
        extends AbstractMojoTestCase {

    public void testGetThemes() throws Exception {
        CompileThemeMojo compileThemeMojo = new CompileThemeMojo();

        setVariableValueToObject(compileThemeMojo, "theme", "theme1");
        assertEquals(1,compileThemeMojo.getThemes().length);
        assertEquals("VAADIN/themes/theme1",compileThemeMojo.getThemes()[0]);

        setVariableValueToObject(compileThemeMojo, "theme", "theme1,theme2");
        assertEquals(2,compileThemeMojo.getThemes().length);
        assertEquals("VAADIN/themes/theme1",compileThemeMojo.getThemes()[0]);
        assertEquals("VAADIN/themes/theme2",compileThemeMojo.getThemes()[1]);
    }

    public void testGetThemesWithEmptySpaces() throws Exception {
        CompileThemeMojo compileThemeMojo = new CompileThemeMojo();

        setVariableValueToObject(compileThemeMojo, "theme", " theme1 ");
        assertEquals(1,compileThemeMojo.getThemes().length);
        assertEquals("VAADIN/themes/theme1",compileThemeMojo.getThemes()[0]);

        setVariableValueToObject(compileThemeMojo, "theme", " theme1 ,theme2 ");
        assertEquals(2,compileThemeMojo.getThemes().length);
        assertEquals("VAADIN/themes/theme1",compileThemeMojo.getThemes()[0]);
        assertEquals("VAADIN/themes/theme2",compileThemeMojo.getThemes()[1]);
    }
}
