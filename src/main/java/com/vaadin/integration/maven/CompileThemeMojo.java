package com.vaadin.integration.maven;

import java.io.File;
import java.util.Collection;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.mojo.gwt.shell.JavaCommand;
import org.codehaus.mojo.gwt.shell.JavaCommandException;

/**
 * Updates Vaadin themes based on addons containing themes on the classpath.
 */
@Mojo(name = "compile-theme", defaultPhase = LifecyclePhase.GENERATE_RESOURCES, requiresDependencyResolution = ResolutionScope.COMPILE)
public class CompileThemeMojo extends AbstractThemeMojo {
    public static final String THEME_COMPILE_CLASS = "com.vaadin.sass.SassCompiler";

    /**
     * Create a compressed version of the theme alongside with the uncompressed one or not.
     */
    @Parameter(defaultValue = "false", property = "vaadin.theme.compress")
    private boolean compressTheme;

    /**
     * Ignore theme compilation warnings or not.
     */
    @Parameter(defaultValue = "false", property = "vaadin.theme.ignore.warnings")
    private boolean ignoreThemeWarnings;

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

        JavaCommand cmd = new JavaCommand();
        cmd.setMainClass(THEME_COMPILE_CLASS);
        cmd.setLog(getLog());

        if (compressTheme) {
            cmd.arg("-compress:true");
        }

        if (ignoreThemeWarnings) {
            cmd.arg("-ignore-warnings:true");
        }

        // src/main/webapp first on classpath
        cmd.addToClasspath(warSourceDirectory);

        // rest of classpath (elements both from plugin and from project)
        Collection<File> classpath = getClasspath(Artifact.SCOPE_COMPILE);
        getLog().debug("Additional classpath elements for vaadin:compile-theme:");
        for (File artifact : classpath) {
            getLog().debug("  " + artifact.getAbsolutePath());
            cmd.addToClasspath(artifact);
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
