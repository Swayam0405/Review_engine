package review_engine.project;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Retains the project's original diagnostic endpoint.
 */
@RestController
public class TestController {

    /**
     * Returns a simple response for checking that the HTTP application is available.
     *
     * @return diagnostic status text
     */
    @GetMapping("/api/test")
    public String test() {
        return "Spring Boot is working!";
    }
}