package ua.lab3.inference;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** One codebase, two roles chosen by the Spring profile: "inference" (scores, REST) or "adaptation" (learns from feedback). */
@SpringBootApplication
public class InferenceApplication {
    public static void main(String[] args) {
        SpringApplication.run(InferenceApplication.class, args);
    }
}
