package com.vaadin.integration.maven;

import java.io.File;
import java.util.Collection;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.mojo.gwt.AbstractGwtMojo;
import org.codehaus.mojo.gwt.shell.JavaCommand;
import org.codehaus.mojo.gwt.shell.JavaCommandException;
import org.codehaus.mojo.gwt.shell.JavaCommandRequest;
import org.codehaus.plexus.util.DirectoryScanner;

/**
 * Updates Vaadin themes based on addons containing themes on the classpath.
 *
 * @goal update-theme
 * @requiresDependencyResolution compile
 * @phase process-classes
 */
public class UpdateThemeMojo extends AbstractGwtMojo {
    public static final String THEME_UPDATE_CLASS = "com.vaadin.server.themeutils.SASSAddonImportFileCreator";

    /**
     * A single theme. Option to specify a single module from command line
     *
     * @parameter expression="${vaadin.theme}"
     */
    private String theme;

    @Override
    public final void execute() throws MojoExecutionException,
            MojoFailureException {
        if ("pom".equals(getProject().getPackaging())) {
            getLog().info("Theme update is skipped");
            return;
        }

        // restrict to Vaadin 7.1 and later, otherwise skip and log
        if (!isVaadin71()) {
            getLog().error("Theme update is only supported for Vaadin 7.1 and later.");
            throw new MojoExecutionException("The goal update-theme requires Vaadin 7.1 or later");
        }

        // update one theme at a time
        String[] themes = getThemes();
        if (themes.length > 0) {
            for (String theme : themes) {
                updateTheme(theme);
            }
        } else {
            // ask the user to explicitly indicate the theme to update
            getLog().info("No themes to update.");
        }
    }

    @SuppressWarnings("unchecked")
    private boolean isVaadin71() {
        // find "vaadin-shared" and check its version
        for (Artifact artifact : getProjectArtifacts()) {
            if (VAADIN_GROUP_ID.equals(artifact.getGroupId())
                    && "vaadin-shared".equals(artifact.getArtifactId())) {
                ArtifactVersion version71 = new DefaultArtifactVersion(
                        "7.1");
                ArtifactVersion vaadinSharedArtifactVersion = new DefaultArtifactVersion(
                        artifact.getVersion());
                if (vaadinSharedArtifactVersion.compareTo(version71) < 0) {
                    getLog().warn(
                            "You're project declares dependency on vaadin-shared "
                                    + artifact.getVersion()
                                    + ". This plugin is designed for at least Vaadin version "
                                    + version71);
                } else {
                    return true;
                }
            }
        }
        return false;
    }

    private void updateTheme(String theme) throws MojoExecutionException {
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

        // add vaadin-client-compiler - the scanner class is currently there
        File[] clientCompilerJars = getGwtDevJar();
        for (File artifact : clientCompilerJars) {
            getLog().debug("  " + artifact.getAbsolutePath());
        }
        cmd.withinClasspath(clientCompilerJars);

        cmd.arg(new File(warSourceDirectory.getAbsolutePath(), theme).getAbsolutePath());

        try {
            cmd.execute();
            getLog().info("Theme \"" + theme + "\" updated");
        } catch (JavaCommandException e) {
            getLog().error("Updating theme \"" + theme + "\" failed", e);
            throw new MojoExecutionException("Updating theme \"" + theme + "\" failed", e);
        }
    }

    /**
     * Return the available themes in the project source/resources folder. If a
     * theme has been set by expression, only that theme is returned.
     *
     * @return the theme names
     */
    public String[] getThemes() {
        // theme has higher priority if set by expression
        if (theme != null) {
            return new String[] { "VAADIN/themes/" + theme };
        }
        String[] themes = new String[0];

        if (warSourceDirectory.exists()) {
            DirectoryScanner scanner = new DirectoryScanner();
            scanner.setBasedir(warSourceDirectory);
            scanner.setIncludes(new String[] { "VAADIN/themes/*" });
            scanner.scan();

            themes = scanner.getIncludedDirectories();
        }

        if (themes.length == 0) {
            getLog().warn("Vaadin plugin could not find any themes.");
        }
        return themes;
    }

}
