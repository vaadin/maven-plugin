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
 * @goal compile-theme
 * @requiresDependencyResolution compile
 * @phase process-classes
 */
public class CompileThemeMojo extends AbstractThemeMojo {
    public static final String THEME_COMPILE_CLASS = "com.vaadin.sass.SassCompiler";

    @Override
    protected void checkVaadinVersion() throws MojoExecutionException {
        // restrict to Vaadin 7.0 and later, otherwise skip and log
        if (!isVaadin7()) {
            getLog().error("Theme compilation is only supported for Vaadin 7.0 and later.");
            throw new MojoExecutionException("The goal compile-theme requires Vaadin 7.0 or later");
        }
    }

    @Override
    protected void processTheme(String theme) throws MojoExecutionException {
        getLog().info("Updating theme " + theme);

        JavaCommandRequest javaCommandRequest = new JavaCommandRequest()
                .setClassName(THEME_COMPILE_CLASS).setLog(getLog());
        JavaCommand cmd = new JavaCommand(javaCommandRequest);

        // src/main/webapp first on classpath
        cmd.withinClasspath(warSourceDirectory);

        // rest of classpath (elements both from plugin and from project)
        Collection<File> classpath = getClasspath(Artifact.SCOPE_COMPILE);
        getLog().debug("Additional classpath elements for vaadin:compile-theme:");
        for (File artifact : classpath) {
            getLog().debug("  " + artifact.getAbsolutePath());
            cmd.withinClasspath(artifact);
        }

        File themeDir = new File(warSourceDirectory.getAbsolutePath(), theme);
        File scssFile = new File(themeDir, "styles.scss");
        File cssFile = new File(themeDir, "styles.css");

        cmd.arg(scssFile.getAbsolutePath());
        cmd.arg(cssFile.getAbsolutePath());

        try {
            cmd.execute();
            getLog().info("Theme \"" + theme + "\" compiled");
        } catch (JavaCommandException e) {
            getLog().error("Compiling theme \"" + theme + "\" failed", e);
            throw new MojoExecutionException("Compiling theme \"" + theme + "\" failed", e);
        }
    }

}
