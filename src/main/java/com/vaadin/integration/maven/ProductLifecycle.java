package com.vaadin.integration.maven;

import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.mojo.gwt.AbstractGwtMojo;

import com.vaadin.pro.licensechecker.BuildType;
import com.vaadin.pro.licensechecker.LicenseChecker;
import com.vaadin.pro.licensechecker.LicenseException;

public class ProductLifecycle {

    public static void validate(AbstractGwtMojo mojo) throws MojoFailureException {

        // Figure out Vaadin version
        String vaadinVersion = null;
        Set<Artifact> artifacts = mojo.getProject().getArtifacts();
        for (Artifact artifact : artifacts) {
            // Store the vaadin version
            if ("vaadin-server".equals(artifact.getArtifactId()) ||
                    "vaadin-server-mpr-jakarta".equals(artifact.getArtifactId())) {
                vaadinVersion = artifact.getVersion();
            }
        }
        if (vaadinVersion == null) {
            throw new IllegalStateException("Unable to determine Vaadin version: " +
                    "'com.vaadin:vaadin-server' or 'com.vaadin:vaadin-server-mpr-jakarta' " +
                    "not found in project dependencies.");
        }

        try {
            // Always check for Vaadin Framework license
            BuildType bt = null;
            LicenseChecker.checkLicense("vaadin-framework", vaadinVersion, bt);
        } catch (LicenseException ex) {
            mojo.getLog().error("Vaadin version check failed", ex);

            throw new MojoFailureException(
                ex, ex.getMessage(),
                "Vaadin license checking failed. Make sure you have a valid " +
                "Vaadin development license, and that it is accessible to the " +
                "license checker. For more information, see " + 
                "https://vaadin.com/licensing-faq-and-troubleshooting");
        }
    }
}
