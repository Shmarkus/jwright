package ee.jwright.core.api;

import java.nio.file.Path;

/**
 * Result of initializing a project for jwright.
 * <p>
 * Contains paths to the created configuration file and templates directory.
 * </p>
 *
 * <h2>Stability: STABLE</h2>
 * <p>This record is part of the stable API and will not change in backwards-incompatible ways.</p>
 *
 * @param configFile   path to the created configuration file (e.g., .jwright/config.yaml)
 * @param templatesDir path to the templates directory (e.g., .jwright/templates)
 */
public record InitResult(
    Path configFile,
    Path templatesDir
) {}
