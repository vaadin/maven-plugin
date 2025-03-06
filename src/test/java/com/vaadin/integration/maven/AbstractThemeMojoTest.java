package com.vaadin.integration.maven;

import org.apache.maven.plugin.testing.AbstractMojoTestCase;

public class AbstractThemeMojoTest extends AbstractMojoTestCase {

    public void testGetThemes() throws Exception {
        CompileThemeMojo compileThemeMojo = new CompileThemeMojo();

        setVariableValueToObject(compileThemeMojo, "theme", "theme1");
        assertEquals(1, compileThemeMojo.getThemes().length);
        assertEquals("VAADIN/themes/theme1", compileThemeMojo.getThemes()[0]);

        setVariableValueToObject(compileThemeMojo, "theme", "theme1,theme2");
        assertEquals(2, compileThemeMojo.getThemes().length);
        assertEquals("VAADIN/themes/theme1", compileThemeMojo.getThemes()[0]);
        assertEquals("VAADIN/themes/theme2", compileThemeMojo.getThemes()[1]);
    }

    public void testGetThemesWithEmptySpaces() throws Exception {
        CompileThemeMojo compileThemeMojo = new CompileThemeMojo();

        setVariableValueToObject(compileThemeMojo, "theme", " theme1 ");
        assertEquals(1, compileThemeMojo.getThemes().length);
        assertEquals("VAADIN/themes/theme1", compileThemeMojo.getThemes()[0]);

        setVariableValueToObject(compileThemeMojo, "theme", " theme1 ,theme2 ");
        assertEquals(2, compileThemeMojo.getThemes().length);
        assertEquals("VAADIN/themes/theme1", compileThemeMojo.getThemes()[0]);
        assertEquals("VAADIN/themes/theme2", compileThemeMojo.getThemes()[1]);
    }
}
