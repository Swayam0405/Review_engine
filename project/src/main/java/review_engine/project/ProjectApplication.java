package review_engine.project;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Bootstraps the Review Engine backend and its Spring-managed application components.
 */
@SpringBootApplication
@EnableScheduling
public class ProjectApplication {

	/**
	 * Starts the embedded server and initializes configured persistence and security.
	 *
	 * @param args command-line arguments passed to Spring Boot
	 */
	public static void main(String[] args) {
		SpringApplication.run(ProjectApplication.class, args);
	}

}
