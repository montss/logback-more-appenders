package compat;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Starts Spring Boot, logs three events tagged with the token passed as the last argument, and exits.
 * compat/check.sh then looks for those events on the console and in Fluentd.
 */
@SpringBootApplication
public class App {

    private static final Logger LOG = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) {
        String token = args.length > 0 ? args[args.length - 1] : "no-token";
        SpringApplication.run(App.class, args).close();
        LOG.info("compat-check info {}", token);
        LOG.warn("compat-check warn {} with {} and {}", token, "arg1", 42);
        LOG.error("compat-check error {}", token, new IOException("expected test exception"));
    }
}
