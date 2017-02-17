package com.vaadin.integration.maven;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.mojo.gwt.AbstractGwtMojo;

import com.vaadin.framework8.migrate.Migrate;

/**
 * Updates Vaadin 7 class imports to their corresponding compatibility imports
 * in a Vaadin 8 project. Additionally updates declarative files to use the correct
 * versions of components.
 */
@Mojo(name = "upgrade8", requiresDependencyResolution = ResolutionScope.COMPILE)
public class Vaadin8UpgradeMojo extends AbstractGwtMojo {
    /**
     * The vaadin version to use for upgrading.
     */
    @Parameter(property = "vaadin.version")
    private String vaadinVersion;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (vaadinVersion == null) {
            vaadinVersion = getVaadinVersion();
        }
        if (vaadinVersion == null) {
            throw new MojoExecutionException(
                    "Unable to determine the Vaadin version for the project. Use -Dvaadin.version=<version>");
        }

        if (!vaadinVersion.startsWith("8.")) {
            throw new MojoExecutionException("Unexpected Vaadin version ("
                    + vaadinVersion
                    + "). Upgrade the project to Vaadin 8 or use -Dvaadin.version=<version> with a version starting with 8");
        }
        try {
            Migrate.main(new String[] { "-version=" + vaadinVersion });
        } catch (Exception e) {
            throw new MojoFailureException("Problem migrating the project", e);
        }
    }

    protected String getVaadinVersion() {
        // find "vaadin-shared" and check its version
        for (Artifact artifact : getProjectArtifacts()) {
            if (VAADIN_GROUP_ID.equals(artifact.getGroupId())
                    && "vaadin-shared".equals(artifact.getArtifactId())) {
                return artifact.getVersion();
            }
        }

        return null;
    }

}
