package com.vaadin.integration.maven;

import java.io.File;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.mojo.gwt.shell.JavaCommand;
import org.codehaus.mojo.gwt.shell.JavaCommandException;

/**
 * Updates Vaadin themes based on addons containing themes on the classpath.
 *
 * This goal is linked to phase generate-sources to make sure it is executed
 * before compile-theme.
 */
@Mojo(name = "update-theme", defaultPhase = LifecyclePhase.GENERATE_SOURCES, requiresDependencyResolution = ResolutionScope.COMPILE)
public class UpdateThemeMojo extends AbstractThemeMojo {
    public static final String THEME_UPDATE_CLASS = "com.vaadin.server.themeutils.SASSAddonImportFileCreator";

    @Override
    protected void checkVaadinVersion() throws MojoExecutionException {
        // restrict to Vaadin 7.1 and later, otherwise skip and log
        if (!isVaadin71()) {
            getLog().error(
                    "Theme update is only supported for Vaadin 7.1 and later.");
            throw new MojoExecutionException(
                    "The goal update-theme requires Vaadin 7.1 or later");
        }
    }

    @Override
    protected void processTheme(String theme) throws MojoExecutionException {
        getLog().info("Updating theme " + theme);

        JavaCommand cmd = createJavaCommand();
        cmd.setMainClass(THEME_UPDATE_CLASS);

        File themeDir = configureThemeClasspath(cmd, theme);

        cmd.arg(themeDir.getAbsolutePath());

        try {
            cmd.execute();
            getLog().info("Theme \"" + theme + "\" updated");
        } catch (JavaCommandException e) {
            getLog().error("Updating theme \"" + theme + "\" failed", e);
            throw new MojoExecutionException(
                    "Updating theme \"" + theme + "\" failed", e);
        }
    }

}
