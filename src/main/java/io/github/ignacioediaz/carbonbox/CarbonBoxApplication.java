package io.github.ignacioediaz.carbonbox;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CarbonBoxApplication {

    public static void main(String[] args) {
        SpringApplication.run(CarbonBoxApplication.class, args);
    }
}
