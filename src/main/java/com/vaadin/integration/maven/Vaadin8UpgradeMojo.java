package com.vaadin.integration.maven;

import java.io.File;
import java.util.Collections;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.MavenArtifactRepository;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.mojo.gwt.shell.AbstractGwtShellMojo;
import org.codehaus.mojo.gwt.shell.JavaCommand;

/**
 * Updates Vaadin 7 class imports to their corresponding compatibility imports
 * in a Vaadin 8 project. Additionally updates declarative files to use the
 * correct versions of components.
 */
@Mojo(name = "upgrade8", requiresDependencyResolution = ResolutionScope.COMPILE)
public class Vaadin8UpgradeMojo extends AbstractGwtShellMojo {
    /**
     * The vaadin version to use for upgrading.
     */
    @Parameter(property = "vaadin.version")
    private String vaadinVersion;

    private File getMigrationJarFile() throws MojoExecutionException {
        // TODO alternatively, could use Aether directly but with risk of
        // version conflicts
        Artifact rootArtifact = artifactFactory.createArtifact(VAADIN_GROUP_ID,
                "framework8-migration-tool", "8.0-SNAPSHOT", "compile", "jar");
        ArtifactRepository vaadinSnapshotRepository = new MavenArtifactRepository(
                "vaadin-snapshots",
                "https://oss.sonatype.org/content/repositories/vaadin-snapshots",
                new DefaultRepositoryLayout(),
                new ArtifactRepositoryPolicy(true,
                        ArtifactRepositoryPolicy.UPDATE_POLICY_DAILY,
                        ArtifactRepositoryPolicy.CHECKSUM_POLICY_FAIL),
                new ArtifactRepositoryPolicy(false, null, null));
        try {
            // no need for transitive dependencies, as the tool is a
            // self-contained JAR including all dependencies
            resolver.resolve(rootArtifact,
                    Collections.singletonList(vaadinSnapshotRepository),
                    localRepository);
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to resolve artifact", e);
        }
        return rootArtifact.getFile();
    }

    @Override
    public void doExecute()
            throws MojoExecutionException, MojoFailureException {
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
            JavaCommand cmd = createJavaCommand();

            // com.vaadin.framework8.migrate.Migrate
            // .main(new String[] { "-version=" + vaadinVersion });

            File jarFile = getMigrationJarFile();
            cmd.addToClasspath(jarFile);

            cmd.setMainClass("com.vaadin.framework8.migrate.Migrate");
            cmd.arg("-version=" + vaadinVersion);

            cmd.execute();

        } catch (Exception e) {
            getLog().error("Migration to Vaadin 8 failed", e);
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
