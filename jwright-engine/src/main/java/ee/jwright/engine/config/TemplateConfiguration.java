package ee.jwright.engine.config;

import ee.jwright.engine.template.MustacheResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

/**
 * Spring configuration for template engine components.
 */
@Configuration
public class TemplateConfiguration {

    /**
     * Creates the MustacheResolver bean using current working directory and user home.
     *
     * @return the MustacheResolver instance
     */
    @Bean
    public MustacheResolver mustacheResolver() {
        Path projectDir = Path.of(System.getProperty("user.dir"));
        Path userHomeDir = Path.of(System.getProperty("user.home"));
        return new MustacheResolver(projectDir, userHomeDir);
    }
}
