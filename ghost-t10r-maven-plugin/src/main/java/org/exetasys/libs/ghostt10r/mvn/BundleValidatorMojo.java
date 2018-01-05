package org.exetasys.libs.ghostt10r.mvn;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.exetasys.libs.ghostt10r.BundleLoader;
import org.exetasys.libs.ghostt10r.model.MsgSpecError;
import org.exetasys.libs.ghostt10r.model.MsgSpecs;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Mojo(
    name = "bundle-validator",
    defaultPhase = LifecyclePhase.PROCESS_RESOURCES)
public class BundleValidatorMojo extends AbstractMojo {

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
            name = "warnOnly",
            defaultValue = "false",
            property = "ghost-t10r.warnOnly")
    private boolean warnOnly;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        Log log = getLog();

        File baseDir = project.getBasedir();

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

        String prefix = keyPrefix == null || keyPrefix.isEmpty()
                ? ""
                : keyPrefix.endsWith(".")
                    ? keyPrefix
                    : keyPrefix + ".";

        BundleLoader loader = new BundleLoader(
                resourcesDirs,
                bundleName,
                prefix,
                mainLocale,
                locales);
        MsgSpecs specs = loader.loadSpecs();

        List<MsgSpecError> errors = specs.errors();
        errors.forEach(e -> {
            if(warnOnly) {
                log.warn(e.getKey() + " -> " + e.getType());
            }
            else {
                log.error(e.getKey() + " -> " + e.getType());
            }
        });
        if (!errors.isEmpty() && !warnOnly) {
            throw new MojoFailureException("Errors in bundle found, see the logs.");
        }
    }
}
