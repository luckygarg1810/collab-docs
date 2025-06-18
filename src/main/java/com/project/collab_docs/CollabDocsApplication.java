package com.project.collab_docs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CollabDocsApplication {

	public static void main(String[] args) {
		SpringApplication.run(CollabDocsApplication.class, args);
	}

}
