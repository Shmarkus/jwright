package ee.jwright.engine.resolve;

import ee.jwright.core.build.BuildTool;
import ee.jwright.core.exception.JwrightException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Resolves the appropriate build tool for a given project directory.
 * <p>
 * Examines the project directory to determine which build system is in use
 * (Maven, Gradle, etc.) and returns the corresponding build tool.
 * When multiple build tools support a project, the one with the lowest order is preferred.
 * </p>
 *
 * <h2>Stability: INTERNAL</h2>
 * <p>This class is internal and may evolve, but honors the stable contracts.</p>
 */
@Component
public class BuildToolResolver {

    private static final Logger log = LoggerFactory.getLogger(BuildToolResolver.class);

    private final List<BuildTool> buildTools;

    /**
     * Creates a new BuildToolResolver with the available build tools.
     *
     * @param buildTools the list of available build tools (order matters for preference)
     */
    @Autowired
    public BuildToolResolver(List<BuildTool> buildTools) {
        this.buildTools = buildTools;
        log.debug("BuildToolResolver initialized with {} build tools: {}",
            buildTools.size(),
            buildTools.stream().map(BuildTool::getId).toList());
    }

    /**
     * Resolves the appropriate build tool for the given project directory.
     *
     * @param projectDir the project directory
     * @return the build tool that supports this project
     * @throws JwrightException if no build tool supports the project
     */
    public BuildTool resolve(Path projectDir) throws JwrightException {
        return tryResolve(projectDir)
            .orElseThrow(() -> new JwrightException(
                JwrightException.ErrorCode.NO_BUILD_TOOL,
                "No build tool found for project: " + projectDir +
                    ". Supported build systems: " + getSupportedBuildSystems()
            ));
    }

    /**
     * Attempts to resolve the appropriate build tool for the given project directory.
     *
     * @param projectDir the project directory
     * @return an Optional containing the build tool if found, empty otherwise
     */
    public Optional<BuildTool> tryResolve(Path projectDir) {
        log.debug("Resolving build tool for: {}", projectDir);

        Optional<BuildTool> result = buildTools.stream()
            .filter(tool -> tool.supports(projectDir))
            .min(Comparator.comparingInt(BuildTool::getOrder));

        result.ifPresentOrElse(
            tool -> log.debug("Resolved build tool for {}: {}", projectDir, tool.getId()),
            () -> log.warn("No build tool found for: {}", projectDir)
        );

        return result;
    }

    /**
     * Returns a list of supported build system identifiers.
     *
     * @return list of build system IDs
     */
    public List<String> getSupportedBuildSystems() {
        return buildTools.stream()
            .map(BuildTool::getId)
            .toList();
    }
}
