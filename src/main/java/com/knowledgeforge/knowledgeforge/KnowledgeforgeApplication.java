package com.knowledgeforge.knowledgeforge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class KnowledgeforgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(KnowledgeforgeApplication.class, args);
    }

}