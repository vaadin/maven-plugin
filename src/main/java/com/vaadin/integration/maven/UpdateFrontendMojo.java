package com.vaadin.integration.maven;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Finds any "bower.json" file in any dependency and includes the dependencies
 * from there.
 * <p>
 * Running this target creates a
 * {@literal src/main/frontend/vaadin-addons/bower.json}, containing the found
 * dependencies. An existing file will be overwritten.
 * <p>
 * When dependencies are found, this goal will also create a project
 * {@literal bower.json} and related files in {@literal src/main/frontend}
 * unless the folder already contains a {@literal bower.json}. These files will
 * only be generated once and never overwritten.
 *
 */
@Mojo(name = "update-frontend", requiresDependencyResolution = ResolutionScope.COMPILE, defaultPhase = LifecyclePhase.GENERATE_RESOURCES)
public class UpdateFrontendMojo extends AbstractMojo {

    private static final String FRONTEND_DIR = "src/main/frontend";

    private static final String APPLICATION_BOWER_JSON = FRONTEND_DIR
            + "/bower.json";
    private static final String APPLICATION_PACKAGE_JSON = FRONTEND_DIR
            + "/package.json";
    private static final String APPLICATION_POLYMER_JSON = FRONTEND_DIR
            + "/polymer.json";
    private static final String APPLICATION_UPDATE_ADDONS_SCRIPT = FRONTEND_DIR
            + "/update-addons-html.js";
    private static final String APPLICATION_BOWER_COMPONENTS_ADDON_CACHE = FRONTEND_DIR
            + "/bower_components/vaadin-addons";
    private static final String ADDONS_BOWER_JSON = FRONTEND_DIR
            + "/vaadin-addons/bower.json";

    @Parameter(property = "project", defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject mavenProject;

    private ObjectMapper mapper = new ObjectMapper();

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        getLog().info("Scanning for bower.json files in dependencies");
        LinkedHashMap<String, String> currentBowerJsonDeps = getCurrentBowerJsonDeps();
        LinkedHashMap<String, String> found = new LinkedHashMap<>();
        for (Artifact artifact : mavenProject.getArtifacts()) {
            if (!"jar".equalsIgnoreCase(artifact.getType())) {
                continue;
            }

            try (JarFile jar = new JarFile(artifact.getFile())) {
                getLog().debug("Scanning " + jar.getName() + " for bower.json");
                ZipEntry bowerJsonFile = jar.getEntry("bower.json");
                if (bowerJsonFile == null) {
                    continue;
                }
                getLog().debug("Scanning bower.json in " + jar.getName());
                try (InputStream stream = jar.getInputStream(bowerJsonFile)) {
                    found.putAll(getDependencies(stream));
                }
            } catch (IOException e) {
                getLog().warn("Error scanning JAR file "
                        + artifact.getFile().getAbsolutePath());
            }
        }
        if (!currentBowerJsonDeps.equals(found)) {
            getLog().info("Dependencies have been updated");
            generateAddonBowerJson(found);
            removeCachedAddonBowerFolder();
        }

        if (!found.isEmpty() && !applicationBowerJsonExists()) {
            createDefaultApplicationFiles();
        }
    }

    private void createDefaultApplicationFiles() {
        writeTemplate("application-bower.json", APPLICATION_BOWER_JSON);
        writeTemplate("application-package.json", APPLICATION_PACKAGE_JSON);
        writeTemplate("application-update-addons-html.js",
                APPLICATION_UPDATE_ADDONS_SCRIPT);
        writeTemplate("application-polymer.json", APPLICATION_POLYMER_JSON);
    }

    private void writeTemplate(String templateName, String targetFile) {
        try {
            String contents = getTemplate(templateName);

            try (FileOutputStream out = new FileOutputStream(targetFile)) {
                IOUtils.write(contents, out, "UTF-8");
            }
        } catch (IOException e) {
            getLog().error("Unable to create " + targetFile, e);
        }

    }

    private String getTemplate(String name) throws IOException {
        return IOUtils.toString(getClass().getResourceAsStream(name), "UTF-8")
                .replaceAll("#artifactId#", mavenProject.getArtifactId());
    }

    private boolean applicationBowerJsonExists() {
        return new File(APPLICATION_BOWER_JSON).exists();
    }

    private void removeCachedAddonBowerFolder() {
        File cacheDir = new File(APPLICATION_BOWER_COMPONENTS_ADDON_CACHE);
        if (cacheDir.exists()) {
            try {
                FileUtils.deleteDirectory(cacheDir);
            } catch (IOException e) {
                getLog().warn("Unable to delete cache directory "
                        + cacheDir.getAbsolutePath()
                        + ". Delete the directory manually to ensure you are using the correct dependencies");
            }
        }

    }

    private void generateAddonBowerJson(LinkedHashMap<String, String> found)
            throws MojoExecutionException {
        File addonsBowerJson = new File(ADDONS_BOWER_JSON);
        getLog().info("Updating " + addonsBowerJson.getAbsolutePath()
                + " with the new dependencies");

        // Remove old and generate a new one
        if (addonsBowerJson.exists()) {
            if (!addonsBowerJson.delete()) {
                throw new MojoExecutionException(
                        "Unable to delete old addons bower.json ("
                                + addonsBowerJson.getAbsolutePath() + ")");
            }
        }

        // Create the directories if needed
        addonsBowerJson.getParentFile().mkdirs();

        String addonsBowerJsonTemplate;
        try {
            addonsBowerJsonTemplate = getTemplate("addons-bower.json");

            ObjectNode addonsBower = (ObjectNode) mapper
                    .readTree(addonsBowerJsonTemplate);
            addonsBower.set("dependencies", dependenciesToJson(found));
            try (FileOutputStream stream = new FileOutputStream(
                    addonsBowerJson)) {
                IOUtils.write(mapper.writeValueAsString(addonsBower), stream,
                        "UTF-8");
            }
        } catch (IOException e) {
            throw new MojoExecutionException(
                    "Unable to delete old addons bower.json ("
                            + addonsBowerJson.getAbsolutePath() + ")");
        }

    }

    private JsonNode dependenciesToJson(LinkedHashMap<String, String> found) {
        return mapper.convertValue(found, JsonNode.class);
    }

    private LinkedHashMap<String, String> getDependencies(
            InputStream bowerJsonStream) throws IOException {
        LinkedHashMap<String, String> dependencies = new LinkedHashMap<>();
        JsonNode node = mapper.readTree(bowerJsonStream);
        if (node.has("dependencies")) {
            JsonNode deps = node.get("dependencies");
            deps.fields()
                    .forEachRemaining(new Consumer<Entry<String, JsonNode>>() {
                        @Override
                        public void accept(Entry<String, JsonNode> entry) {
                            String key = entry.getKey();
                            String value = entry.getValue().asText();
                            getLog().debug(
                                    "Found dependency: " + key + ": " + value);
                            dependencies.put(key, value);
                        }
                    });
        }
        return dependencies;
    }

    /**
     * Finds the dependencies defined in the addons bower.json.
     *
     * @return
     * @throws MojoExecutionException
     * @throws IOException
     */
    private LinkedHashMap<String, String> getCurrentBowerJsonDeps()
            throws MojoExecutionException {
        LinkedHashMap<String, String> dependencies = new LinkedHashMap<>();
        File addonsBowerJson = new File(ADDONS_BOWER_JSON);
        if (!addonsBowerJson.exists()) {
            return dependencies;
        }

        getLog().debug(
                "Scanning bower.json in " + addonsBowerJson.getAbsolutePath());
        try (InputStream stream = new FileInputStream(addonsBowerJson)) {
            dependencies.putAll(getDependencies(stream));
        } catch (IOException e) {
            throw new MojoExecutionException(
                    "Unable to parse project addons json ("
                            + addonsBowerJson.getAbsolutePath() + ")",
                    e);
        }

        return dependencies;
    }

}
