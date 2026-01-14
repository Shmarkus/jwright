package ee.jwright.core.template;

import java.util.Map;

/**
 * A pluggable template engine for rendering prompts.
 * <p>
 * Implementations render template files with variable substitution.
 * Templates are used to generate LLM prompts with context from extraction.
 * </p>
 *
 * <h2>Template Resolution Order</h2>
 * <ol>
 *   <li>{@code .jwright/templates/{name}} - project-specific</li>
 *   <li>{@code ~/.jwright/templates/{name}} - user global</li>
 *   <li>Bundled defaults (in JAR resources)</li>
 * </ol>
 *
 * <h2>Stability: STABLE</h2>
 * <p>This interface is part of the stable API and will not change in backwards-incompatible ways.</p>
 */
public interface TemplateEngine {

    /**
     * Renders a template with the given variables.
     * <p>
     * Looks up the template by name, substitutes variables, and returns the result.
     * </p>
     *
     * @param templateName the name of the template (e.g., "implement.mustache")
     * @param variables    map of variable names to values
     * @return the rendered template content
     * @throws IllegalArgumentException if the template is not found
     */
    String render(String templateName, Map<String, Object> variables);

    /**
     * Checks if a template exists.
     *
     * @param templateName the name of the template
     * @return true if the template exists in any of the resolution paths
     */
    boolean templateExists(String templateName);
}
