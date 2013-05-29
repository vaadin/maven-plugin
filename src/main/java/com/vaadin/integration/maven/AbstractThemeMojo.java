package com.vaadin.integration.maven;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.mojo.gwt.AbstractGwtMojo;
import org.codehaus.plexus.util.DirectoryScanner;

/**
 * Abstract base class for theme compilation related Mojos.
 */
public abstract class AbstractThemeMojo extends AbstractGwtMojo {
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
            if (VAADIN_GROUP_ID.equals(artifact.getGroupId())
                    && "vaadin-shared".equals(artifact.getArtifactId())) {
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
                        getLog().info("Failed to parse vaadin-shared version number "+artifactVersion);
                    }
                }
                if (!isNewer) {
                    getLog().warn(
                            "Your project declares dependency on vaadin-shared "
                                    + artifactVersion
                                    + ". This goal requires at least Vaadin version "+major+"."+minor);
                } else {
                    return true;
                }
            }
        }
        return false;
    }

    protected abstract void processTheme(String theme) throws MojoExecutionException;

    /**
     * Return the available themes in the project source/resources folder. If a
     * theme has been set by expression, only that theme is returned.
     *
     * @return the theme names
     */
    protected String[] getThemes() {
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
