package ee.jwright.engine.template;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import ee.jwright.core.template.TemplateEngine;
import org.springframework.stereotype.Component;

import java.io.Reader;
import java.io.StringWriter;
import java.util.Map;

/**
 * Mustache-based implementation of {@link TemplateEngine}.
 * <p>
 * Uses the Mustache.java library to render templates. Templates are resolved
 * via {@link MustacheResolver} which checks project, user, and classpath locations.
 * </p>
 *
 * <h2>Stability: INTERNAL</h2>
 * <p>This class is internal and may evolve, but honors the stable contracts.</p>
 */
@Component
public class MustacheTemplateEngine implements TemplateEngine {

    private final MustacheFactory mustacheFactory;
    private final MustacheResolver resolver;

    /**
     * Creates a new MustacheTemplateEngine with the given resolver.
     *
     * @param resolver the template resolver
     */
    public MustacheTemplateEngine(MustacheResolver resolver) {
        this.resolver = resolver;
        this.mustacheFactory = new DefaultMustacheFactory(resolver);
    }

    @Override
    public String render(String templateName, Map<String, Object> variables) {
        // First check if template exists
        Reader reader = resolver.resolve(templateName);
        if (reader == null) {
            throw new IllegalArgumentException("Template not found: " + templateName);
        }

        // Close the reader we used for existence check
        try {
            reader.close();
        } catch (Exception ignored) {
            // Ignore close errors
        }

        // Compile and execute the template
        Mustache mustache = mustacheFactory.compile(templateName);
        StringWriter writer = new StringWriter();
        mustache.execute(writer, variables);
        return writer.toString();
    }

    @Override
    public boolean templateExists(String templateName) {
        return resolver.exists(templateName);
    }
}
