package com.github.patching.maven.warsync;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.resolver.ArtifactCollector;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.artifact.resolver.ResolutionListener;
import org.apache.maven.artifact.resolver.ResolutionNode;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.artifact.resolver.filter.ExcludesArtifactFilter;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Exclusion;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.archiver.jar.JarArchiver;
import org.codehaus.plexus.archiver.jar.Manifest;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.StringUtils;

/**
 * generate profile for eclipse fileSync plugin
 */
@Mojo(name = "eclipse", defaultPhase = LifecyclePhase.GENERATE_RESOURCES)
public class WarSyncMojo extends AbstractMojo {

    public static final String        FILE_SYNC_CONFIG_FILE = ".settings/de.loskutov.FileSync.prefs";

    public static final String        LIB_MODE_COPY         = "copy";
    public static final String        LIB_MODE_REF          = "ref";
    private static final List<String> SCOPES                = new ArrayList<String>();

    static {
        SCOPES.add(Artifact.SCOPE_COMPILE);
        SCOPES.add(Artifact.SCOPE_RUNTIME);
    }

    @Component
    private MavenSession              session;

    @Component
    private MavenProject              project;

    @Component
    private ArtifactFactory           artifactFactory;

    @Component
    private ArtifactCollector         artifactCollector;

    @Component
    protected ArtifactResolver        artifactResolver;

    @Component(hint = "maven")
    private ArtifactMetadataSource    artifactMetadataSource;

    @Parameter(property = "reactorProjects", defaultValue = "${reactorProjects}", readonly = true, required = true)
    private List<MavenProject>        reactorProjects;

    /**
     * skip the goal, default is <code>false</code>
     */
    @Parameter(defaultValue = "false", property = "warsync.skip")
    private boolean                   skip;

    /**
     * <strong>copy</strong>: copy the lib jars into <code>warRoot/WEB-INF/lib</code> <br />
     * <strong>ref</strong>: create "warsync-classpath.jar" jar file into <code>warRoot/WEB-INF/lib</code>. A
     * MANIFEST.MF with "Class-Path" entry is included by the jar.</code><br />
     * default is <strong>copy</strong>
     */
    @Parameter(defaultValue = "copy", property = "warsync.libMode")
    private String                    libMode;

    @Parameter(defaultValue = "${project.projectReferences}", readonly = true, required = true)
    private Map<String, MavenProject> projectReferences;

    /**
     * target war directory. <br />
     * default is <strong>${project.build.directory}/warsync/${project.build.finalName}.war</strong>
     */
    @Parameter(defaultValue = "${project.build.directory}/warsync/${project.build.finalName}.war", property = "warsync.dir", required = true)
    private File                      warDir;

    /**
     * webapp root source directory. <br />
     * default is <strong>${basedir}/src/main/webapp</strong>
     */
    @Parameter(defaultValue = "${basedir}/src/main/webapp", property = "warsync.webRootSrc", required = true)
    private File                      warSrcDir;

    /**
     * run the goal if only current build contains a goal with <strong>eclipse:eclipse</strong><br />
     * default is <code>true</code>.
     */
    @Parameter(defaultValue = "true", property = "warsync.runOnlyWithEclipseGoal", required = true)
    private boolean                   runOnlyWithEclipseGoal;

    /**
     * TODO:
     */
    @Parameter
    private Map<String, String>       projectFilters        = new HashMap<String, String>();

    /**
     * resolved artifacts
     */
    @Parameter(defaultValue = "${project.artifacts}", required = true, readonly = true)
    private Set<Artifact>             artifacts;

    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("[WarSync] WarSync is skiped. See ${warsync.skip} parameter.");
            return;
        }
        if (runOnlyWithEclipseGoal && !session.getGoals().contains("eclipse:eclipse")) {
            getLog().info(
                    "[WarSync] WarSync will be skiped for no 'eclipse:eclipse' goal found in current build.\n\tSet ${warsync.runOnlyWithEclipseGoal} to false to disable the feature if you want to force to run.");
            return;
        }
        if (!"war".equals(project.getPackaging())) {
            return;
        }
        try {
            prepareArtifacts();
            if (LIB_MODE_COPY.equals(libMode)) {
                copyLibJars(getClearLibDir());
            } else {
                createClasspathJarFile(getClearLibDir());
            }
            createFileSyncConfigs();
        } catch (Exception e) {
            throw new MojoExecutionException("error execute maven-warsync-plugin:eclipse", e);
        }
    }

    @SuppressWarnings("unchecked")
    private void prepareArtifacts() {
        try {
            Set<Artifact> artifacts = new LinkedHashSet<Artifact>();
            Map<String, Artifact> managedVersions = createManagedVersionMap();
            List<ResolutionListener> listeners = new ArrayList<ResolutionListener>();
            ArtifactResolutionResult resolveResult = artifactCollector.collect(getAllArtifacts(), project.getArtifact(), managedVersions,
                    session.getLocalRepository(), project.getRemoteArtifactRepositories(), artifactMetadataSource, null, listeners);
            for (ResolutionNode node : (Set<ResolutionNode>) resolveResult.getArtifactResolutionNodes()) {
                if (!isReactorProject(node.getArtifact())) {
                    artifactResolver.resolve(node.getArtifact(), node.getRemoteRepositories(), session.getLocalRepository());
                    artifacts.add(node.getArtifact());
                }
            }
            this.artifacts = artifacts;
        } catch (Exception e) {
            getLog().debug("[WarSync] " + e.getMessage(), e);
            getLog().error("[WarSync] " + e.getMessage());
        }
    }

    private void copyLibJars(File lib) throws Exception {
        getLog().info("[WarSync] Copy lib jars to " + lib.getAbsolutePath());
        if (artifacts != null && !artifacts.isEmpty()) {
            for (Artifact artifact : artifacts) {
                if (getLog().isDebugEnabled()) {
                    getLog().debug("[WarSync] Found dependency: " + artifact.getId());
                }
                if (isLib(artifact)) {
                    if (getLog().isDebugEnabled()) {
                        getLog().debug("[WarSync] Copy " + artifact.getFile().getAbsolutePath() + " to " + lib.getAbsolutePath());
                    }
                    FileChannel in = null;
                    FileChannel out = null;
                    try {
                        File srcFile = artifact.getFile();
                        File destFile = new File(lib, srcFile.getName());
                        if (destFile.exists()) {
                            String fileName = artifact.getGroupId() + "-" + srcFile.getName();
                            if (getLog().isDebugEnabled()) {
                                getLog().debug("[WarSync] Rename " + srcFile.getName() + " to " + fileName);
                            }
                            destFile = new File(lib, fileName);
                        }
                        destFile.createNewFile();
                        in = new FileInputStream(srcFile).getChannel();
                        out = new FileOutputStream(destFile).getChannel();
                        in.transferTo(0, in.size(), out);
                    } finally {
                        if (in != null) {
                            in.close();
                        }
                        if (out != null) {
                            out.close();
                        }
                    }
                }
            }
        }
    }

    private void createFileSyncConfigs() throws Exception {
        createFileSyncConfig(project);
        for (MavenProject prj : projectReferences.values()) {
            createFileSyncConfig(prj);
        }
    }

    private void createFileSyncConfig(MavenProject prj) throws Exception {
        File configFile = new File(prj.getBasedir(), FILE_SYNC_CONFIG_FILE);
        getLog().info("[WarSync] Create FileSync Config: " + configFile.getAbsolutePath());
        URI BaseURI = prj.getBasedir().toURI();
        configFile.getParentFile().mkdirs();
        String filterFile = projectFilters.get(getProjectReferenceId(prj.getArtifact()));
        if (filterFile == null && !prj.getFilters().isEmpty()) {
            filterFile = (String) prj.getFilters().get(0);
            if (filterFile != null) {
                filterFile = BaseURI.relativize(new File(filterFile).toURI()).getPath();
            }
        }
        PrintWriter writer = null;
        try {
            int index = 0;
            writer = new PrintWriter(configFile);
            writer.println("defaultDestination=" + FileSyncMap.formatPath(warDir.getAbsolutePath()));
            writer.println("eclipse.preferences.version=1");
            writer.println("includeTeamPrivateFiles=false");
            writer.println("useCurrentDateForDestinationFiles=false");
            if (prj == project) {
                FileSyncMap webRootMap = new FileSyncMap(index++);
                webRootMap.setSourceFolder(BaseURI.relativize(warSrcDir.toURI()).getPath());
                webRootMap.setExcludePatterns("WEB-INF/");
                writer.println(webRootMap.render());

                FileSyncMap webInfoMap = new FileSyncMap(index++);
                webInfoMap.setSourceFolder(BaseURI.relativize(new File(warSrcDir, "WEB-INF").toURI()).getPath());
                webInfoMap.setDestFolder(new File(warDir, "WEB-INF").getAbsolutePath());
                webInfoMap.setFilterFile(filterFile);
                writer.println(webInfoMap.render());
            }

            FileSyncMap classMap = new FileSyncMap(index++);
            classMap.setSourceFolder(BaseURI.relativize(new File(prj.getBuild().getOutputDirectory()).toURI()).getPath());
            classMap.setDestFolder(new File(warDir, "WEB-INF/classes").getAbsolutePath());
            classMap.setFilterFile(filterFile);
            writer.println(classMap.render());
        } finally {
            if (writer != null) {
                writer.flush();
                writer.close();
            }
        }
    }

    private void createClasspathJarFile(File lib) throws Exception {
        getLog().info("[WarSync] Create warsync-classpath.jar in " + lib.getAbsolutePath());
        List<String> jarFiles = new ArrayList<String>();
        if (artifacts != null && !artifacts.isEmpty()) {
            for (Artifact artifact : artifacts) {
                if (getLog().isDebugEnabled()) {
                    getLog().debug("[WarSync] Found dependency: " + artifact.getId());
                }
                if (isLib(artifact)) {
                    if (getLog().isDebugEnabled()) {
                        getLog().debug("[WarSync] Add " + artifact.getFile().toURI().getPath() + " to Class-Path entry of MANIFEST.MF");
                    }
                    jarFiles.add(artifact.getFile().toURI().getPath());
                }
            }
        }
        JarArchiver jar = new JarArchiver();
        jar.setDestFile(new File(lib, "warsync-classpath.jar"));
        Manifest manifest = Manifest.getDefaultManifest();
        manifest.addConfiguredAttribute(new Manifest.Attribute("Created-By", "maven-warsync-plugin"));
        manifest.addConfiguredAttribute(new Manifest.Attribute("Built-By", System.getProperty("user.name")));
        manifest.addConfiguredAttribute(new Manifest.Attribute("Build-Jdk", System.getProperty("java.version")));
        if (!jarFiles.isEmpty()) {
            manifest.addConfiguredAttribute(new Manifest.Attribute("Class-Path", StringUtils.join(jarFiles.iterator(), " ")));
        }
        jar.addConfiguredManifest(manifest);
        jar.createArchive();
    }

    private File getClearLibDir() throws Exception {
        File lib = new File(warDir, "WEB-INF/lib/");
        lib.mkdirs();
        FileUtils.cleanDirectory(lib);
        return lib;
    }

    private String getProjectReferenceId(Artifact artifact) {
        return artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getBaseVersion();
    }

    private boolean isLib(Artifact artifact) {
        return artifact.isResolved() && SCOPES.contains(artifact.getScope()) && "jar".equals(artifact.getType())
               && projectReferences.get(getProjectReferenceId(artifact)) == null;
    }

    private Map<String, Artifact> createManagedVersionMap() throws MojoExecutionException {
        Map<String, Artifact> map = new HashMap<String, Artifact>();
        DependencyManagement dependencyManagement = project.getDependencyManagement();
        if (dependencyManagement != null && dependencyManagement.getDependencies() != null) {
            for (Dependency d : dependencyManagement.getDependencies()) {
                try {
                    VersionRange versionRange = VersionRange.createFromVersionSpec(d.getVersion());
                    Artifact artifact = artifactFactory.createDependencyArtifact(d.getGroupId(), d.getArtifactId(), versionRange,
                            d.getType(), d.getClassifier(), d.getScope(), d.isOptional());
                    handleExclusions(artifact, d);
                    map.put(d.getManagementKey(), artifact);
                } catch (InvalidVersionSpecificationException e) {
                    throw new MojoExecutionException(String.format("%1s: unable to parse version '%2s' for dependency '%3s': %4s",
                            project.getId(), d.getVersion(), d.getManagementKey(), e.getMessage()), e);
                }
            }
        }
        return map;
    }

    private void handleExclusions(Artifact artifact, Dependency dependency) {
        List<String> exclusions = new ArrayList<String>();
        for (Exclusion exclusion : dependency.getExclusions()) {
            exclusions.add(exclusion.getGroupId() + ":" + exclusion.getArtifactId());
        }
        ArtifactFilter filter = new ExcludesArtifactFilter(exclusions);
        artifact.setDependencyFilter(filter);
    }

    @SuppressWarnings("unchecked")
    private Set<Artifact> getAllArtifacts() throws MojoExecutionException {
        Set<Artifact> artifacts = new LinkedHashSet<Artifact>();
        for (Dependency dep : (List<Dependency>) project.getDependencies()) {
            VersionRange versionRange;
            try {
                versionRange = VersionRange.createFromVersionSpec(dep.getVersion());
            } catch (InvalidVersionSpecificationException e) {
                throw new MojoExecutionException(String.format("%1s: unable to parse version '%2s' for dependency '%3s': %4s",
                        dep.getArtifactId(), dep.getVersion(), dep.getManagementKey(), e.getMessage()), e);
            }
            String type = dep.getType() == null ? "jar" : dep.getType();
            boolean optional = dep.isOptional();
            String scope = dep.getScope() == null ? Artifact.SCOPE_COMPILE : dep.getScope();
            Artifact artifact = artifactFactory.createDependencyArtifact(dep.getGroupId(), dep.getArtifactId(), versionRange, type,
                    dep.getClassifier(), scope, optional);
            if (scope.equalsIgnoreCase(Artifact.SCOPE_SYSTEM)) {
                artifact.setFile(new File(dep.getSystemPath()));
            }
            handleExclusions(artifact, dep);
            artifacts.add(artifact);
        }
        return artifacts;
    }

    private boolean isReactorProject(Artifact artifact) {
        if (reactorProjects != null) {
            for (MavenProject project : reactorProjects) {
                if (project.getGroupId().equals(artifact.getGroupId()) && project.getArtifactId().equals(artifact.getArtifactId())) {
                    if (project.getVersion().equals(artifact.getVersion())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
