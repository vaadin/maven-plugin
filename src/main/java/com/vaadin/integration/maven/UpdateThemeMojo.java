package com.vaadin.integration.maven;

import java.io.File;
import java.util.Collection;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.mojo.gwt.shell.JavaCommand;
import org.codehaus.mojo.gwt.shell.JavaCommandException;
import org.codehaus.mojo.gwt.shell.JavaCommandRequest;

/**
 * Updates Vaadin themes based on addons containing themes on the classpath.
 *
 * This goal is linked to phase generate-sources to make sure it is executed before compile-theme.
 *
 * @goal update-theme
 * @requiresDependencyResolution compile
 * @phase generate-sources
 */
public class UpdateThemeMojo extends AbstractThemeMojo {
    public static final String THEME_UPDATE_CLASS = "com.vaadin.server.themeutils.SASSAddonImportFileCreator";


    @Override
    protected void checkVaadinVersion() throws MojoExecutionException {
        // restrict to Vaadin 7.1 and later, otherwise skip and log
        if (!isVaadin71()) {
            getLog().error("Theme update is only supported for Vaadin 7.1 and later.");
            throw new MojoExecutionException("The goal update-theme requires Vaadin 7.1 or later");
        }
    }

    @Override
    protected void processTheme(String theme) throws MojoExecutionException {
        getLog().info("Updating theme " + theme);

        JavaCommandRequest javaCommandRequest = new JavaCommandRequest()
                .setClassName(THEME_UPDATE_CLASS).setLog(getLog());
        JavaCommand cmd = new JavaCommand(javaCommandRequest);

        // src/main/webapp first on classpath
        cmd.withinClasspath(warSourceDirectory);

        // rest of classpath (elements both from plugin and from project)
        Collection<File> classpath = getClasspath(Artifact.SCOPE_COMPILE);
        getLog().debug("Additional classpath elements for vaadin:update-theme:");
        for (File artifact : classpath) {
            getLog().debug("  " + artifact.getAbsolutePath());
            cmd.withinClasspath(artifact);
        }

        cmd.arg(new File(warSourceDirectory.getAbsolutePath(), theme).getAbsolutePath());

        try {
            cmd.execute();
            getLog().info("Theme \"" + theme + "\" updated");
        } catch (JavaCommandException e) {
            getLog().error("Updating theme \"" + theme + "\" failed", e);
            throw new MojoExecutionException("Updating theme \"" + theme + "\" failed", e);
        }
    }

}
