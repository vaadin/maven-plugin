package com.vaadin.integration.maven;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.mojo.gwt.shell.AbstractGwtShellMojo;
import org.codehaus.mojo.gwt.shell.JavaCommand;
import org.codehaus.plexus.util.DirectoryScanner;

/**
 * Abstract base class for theme compilation related Mojos.
 */
public abstract class AbstractThemeMojo extends AbstractGwtShellMojo {
    /**
     * A single theme. Option to specify a single module from command line
     */
    @Parameter(property = "vaadin.theme")
    private String theme;

    @Override
    public final void doExecute() throws MojoExecutionException, MojoFailureException {
        if ("pom".equals(getProject().getPackaging())) {
            getLog().info("Theme processing is skipped");
            return;
        }

        checkVaadinVersion();

        // update one theme at a time
        String[] themes = getThemes();
        if (themes.length > 0) {
            for (String theme : themes) {
                processTheme(theme);
            }
        } else {
            // ask the user to explicitly indicate the theme to update
            getLog().info("No themes found. Use the parameter \"vaadin.theme\" to explicitly select a theme.");
        }
    }

    protected abstract void checkVaadinVersion() throws MojoExecutionException;

    protected boolean isVaadin7() {
        return isAtLeastVaadinVersion(7, 0);
    }

    protected boolean isVaadin71() {
        return isAtLeastVaadinVersion(7, 1);
    }

    protected boolean isAtLeastVaadinVersion(int major, int minor) {
        // find "vaadin-shared" and check its version
        for (Artifact artifact : getProjectArtifacts()) {
            if (VAADIN_GROUP_ID.equals(artifact.getGroupId()) && "vaadin-shared".equals(artifact.getArtifactId())) {
                // TODO this is an ugly hack because Maven does not tolerate
                // version numbers of the form "7.1.0.beta1"
                String artifactVersion = artifact.getVersion();
                String[] versionParts = artifactVersion.split("[.-]");
                boolean isNewer = false;
                if (versionParts.length >= 2) {
                    try {
                        int majorVersion = Integer.parseInt(versionParts[0]);
                        int minorVersion = Integer.parseInt(versionParts[1]);
                        if (majorVersion > major || (majorVersion == major && minorVersion >= minor)) {
                            isNewer = true;
                        }
                    } catch (NumberFormatException e) {
                        getLog().info("Failed to parse vaadin-shared version number " + artifactVersion);
                    }
                }
                if (!isNewer) {
                    getLog().warn("Your project declares dependency on vaadin-shared "
                            + artifactVersion
                            + ". This goal requires at least Vaadin version " + major + "." + minor);
                } else {
                    return true;
                }
            }
        }
        return false;
    }

    protected abstract void processTheme(String theme) throws MojoExecutionException;

    /**
     * Configure the classpath for theme update/compilation.
     *
     * @param cmd command for which to configure the classpath
     * @param theme theme path relative to a resource directory
     * @return the first suitable theme directory found (to be used as the output directory)
     * @throws MojoExecutionException
     */
    protected File configureThemeClasspath(JavaCommand cmd, String theme) throws MojoExecutionException {
        File themeDir = null;
        // resource directories with themes first on classpath
        for (Resource res : getProject().getResources()) {
            File resourceDirectory = new File(res.getDirectory());
            if (new File(resourceDirectory, theme).exists()) {
                getLog().debug("Adding resource directory to command classpath: " + resourceDirectory);
                if (themeDir == null) {
                    themeDir = resourceDirectory;
                }
                cmd.addToClasspath(resourceDirectory);
            }
        }

        // src/main/webapp is another common location for theme files
        if (themeDir == null) {
            themeDir = warSourceDirectory;
        }
        cmd.addToClasspath(warSourceDirectory);

        // rest of classpath (elements both from plugin and from project)
        Collection<File> classpath = getClasspath(Artifact.SCOPE_COMPILE);
        getLog().debug("Additional classpath elements for vaadin theme update/compile:");
        for (File artifact : classpath) {
            getLog().debug("  " + artifact.getAbsolutePath());
            cmd.addToClasspath(artifact);
        }

        return new File(themeDir, theme);
    }

    /**
     * Return the available themes in the project source/resources folder. If a
     * theme has been set by expression, only that theme is returned.
     * It is possible to specify more themes, comma separated.
     *
     * @return the theme names
     */
    protected String[] getThemes() {
        // theme has higher priority if set by expression
        if (theme != null) {
            String[] themes = theme.split(",");
            String[] themesPaths = new String[themes.length];
            for (int i = 0; i < themesPaths.length; i++) {
                themesPaths[i] = "VAADIN/themes/" + themes[i].trim();
            }
            return themesPaths;
        }
        List<String> themes = new ArrayList<String>();

        List<Resource> resources = getProject().getResources();
        for (Resource resource : resources) {
            themes.addAll(scanDirectory(new File(resource.getDirectory())));
        }

        if (warSourceDirectory.exists()) {
            themes.addAll(scanDirectory(warSourceDirectory));
        }

        if (themes.isEmpty()) {
            getLog().warn("Vaadin plugin could not find any themes.");
        }
        return themes.toArray(new String[0]);
    }

    private Collection<String> scanDirectory(File baseDir) {
        if (!baseDir.exists()) {
            return Collections.emptyList();
        }
        DirectoryScanner scanner = new DirectoryScanner();
        scanner.setBasedir(baseDir);
        scanner.setIncludes(new String[] {"VAADIN/themes/*"});
        scanner.scan();

        return Arrays.asList(scanner.getIncludedDirectories());
    }
}
