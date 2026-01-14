package ee.jwright.engine.template;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolves Mustache templates from multiple locations.
 * <p>
 * Resolution order:
 * <ol>
 *   <li>.jwright/templates/ in project directory</li>
 *   <li>~/.jwright/templates/ in user home</li>
 *   <li>Classpath (bundled defaults)</li>
 * </ol>
 *
 * <h2>Stability: INTERNAL</h2>
 * <p>This class is internal and may evolve, but honors the stable contracts.</p>
 */
public class MustacheResolver implements com.github.mustachejava.MustacheResolver {

    private static final Logger log = LoggerFactory.getLogger(MustacheResolver.class);
    private static final String TEMPLATES_SUBDIR = ".jwright/templates";
    private static final String CLASSPATH_PREFIX = "templates/";

    private final Path projectDir;
    private final Path userHomeDir;

    /**
     * Creates a new resolver with the given directories.
     *
     * @param projectDir  the project directory (may be null)
     * @param userHomeDir the user home directory (may be null)
     */
    public MustacheResolver(Path projectDir, Path userHomeDir) {
        this.projectDir = projectDir;
        this.userHomeDir = userHomeDir;
    }

    /**
     * Creates a resolver using system properties for user home.
     *
     * @param projectDir the project directory
     * @return the resolver
     */
    public static MustacheResolver forProject(Path projectDir) {
        String userHome = System.getProperty("user.home");
        return new MustacheResolver(projectDir, userHome != null ? Path.of(userHome) : null);
    }

    @Override
    public Reader getReader(String resourceName) {
        // 1. Check project templates
        if (projectDir != null) {
            Path projectTemplate = projectDir.resolve(TEMPLATES_SUBDIR).resolve(resourceName);
            if (Files.exists(projectTemplate)) {
                log.debug("Found template in project: {}", projectTemplate);
                try {
                    return Files.newBufferedReader(projectTemplate, StandardCharsets.UTF_8);
                } catch (Exception e) {
                    log.warn("Failed to read project template: {}", projectTemplate, e);
                }
            }
        }

        // 2. Check user home templates
        if (userHomeDir != null) {
            Path userTemplate = userHomeDir.resolve(TEMPLATES_SUBDIR).resolve(resourceName);
            if (Files.exists(userTemplate)) {
                log.debug("Found template in user home: {}", userTemplate);
                try {
                    return Files.newBufferedReader(userTemplate, StandardCharsets.UTF_8);
                } catch (Exception e) {
                    log.warn("Failed to read user template: {}", userTemplate, e);
                }
            }
        }

        // 3. Check classpath
        String classpathResource = CLASSPATH_PREFIX + resourceName;
        InputStream is = getClass().getClassLoader().getResourceAsStream(classpathResource);
        if (is != null) {
            log.debug("Found template in classpath: {}", classpathResource);
            return new InputStreamReader(is, StandardCharsets.UTF_8);
        }

        log.debug("Template not found: {}", resourceName);
        return null;
    }

    /**
     * Resolves a template and returns the Reader, or null if not found.
     *
     * @param resourceName the template name
     * @return Reader for the template, or null if not found
     */
    public Reader resolve(String resourceName) {
        return getReader(resourceName);
    }

    /**
     * Checks if a template exists in any of the resolution locations.
     *
     * @param templateName the template name
     * @return true if the template exists
     */
    public boolean exists(String templateName) {
        // Check project templates
        if (projectDir != null) {
            Path projectTemplate = projectDir.resolve(TEMPLATES_SUBDIR).resolve(templateName);
            if (Files.exists(projectTemplate)) {
                return true;
            }
        }

        // Check user home templates
        if (userHomeDir != null) {
            Path userTemplate = userHomeDir.resolve(TEMPLATES_SUBDIR).resolve(templateName);
            if (Files.exists(userTemplate)) {
                return true;
            }
        }

        // Check classpath
        String classpathResource = CLASSPATH_PREFIX + templateName;
        return getClass().getClassLoader().getResource(classpathResource) != null;
    }
}
