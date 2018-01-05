package org.exetasys.libs.ghostt10r.mvn;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.exetasys.libs.ghostt10r.MessageClassBuilder;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;


@Mojo(
    name = "ghost-gen",
    defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class GhostGeneratorMojo
        extends AbstractMojo {

    @Component(role = MavenProject.class)
    private MavenProject project;

    @Parameter(
            name = "resourcesPaths",
            alias = "resources",
            required = true,
            property = "ghost-t10r.resources")
    private String[] resourcesPaths;

    @Parameter(
        name = "bundleName",
        required = true,
        property = "ghost-t10r.bundleName")
    private String bundleName;

    @Parameter(
        name = "keyPrefix",
        defaultValue = "",
        property = "ghost-t10r.keyPrefix")
    private String keyPrefix;

    @Parameter(
        name = "mainLocaleDesc",
        alias = "mainLocale",
        required = true,
        property = "ghost-t10r.mainLocale")
    private String mainLocaleDesc;

    @Parameter(
        name = "localesDescs",
        alias = "locales",
        required = true,
        property = "ghost-t10r.locales")
    private String[] localesDescs;

    @Parameter(
            name = "destDirPath",
            alias = "destDir",
            defaultValue = "target/generated-sources/ghost-t10r",
            required = false,
            property = "ghost-t10r.destDir")
    private String destDirPath;

    @Parameter(
            name = "className",
            required = true,
            property = "ghost-t10r.enumName")
    private String className;

    @Parameter(
            name = "classPackage",
            required = true,
            property = "ghost-t10r.classPackage")
    private String classPackage;

    public void execute()
            throws MojoExecutionException {
        Log log = getLog();

        File baseDir = project.getBasedir();
        File destDir = new File(baseDir, destDirPath);
        destDir.mkdirs();

        Locale mainLocale = Locale.forLanguageTag(mainLocaleDesc);
        log.info("Main locale (" + mainLocaleDesc + ") is " + mainLocale.toLanguageTag());

        Set<Locale> locales = Arrays.stream(localesDescs)
            .map(Locale::forLanguageTag)
            .collect(Collectors.toSet());
        locales.add(mainLocale);
        for(Locale l: locales) {
            log.info("Locale found: " + l.toLanguageTag());
        }

        List<File> resourcesDirs = Arrays.stream(resourcesPaths)
            .map(path -> new File(baseDir, path))
            .collect(Collectors.toList());

        String prefix = keyPrefix.endsWith(".") ? keyPrefix : keyPrefix + ".";

        MessageClassBuilder builder = new MessageClassBuilder(
                resourcesDirs,
                bundleName,
                prefix,
                mainLocale,
                locales,
                destDir,
                className,
                classPackage);
        builder.writeEnumFile(builder.loadSpecs());
    }
}
