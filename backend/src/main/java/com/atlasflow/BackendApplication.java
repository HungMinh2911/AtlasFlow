package com.atlasflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BackendApplication {

	public static void main(String[] args) {
		// Use one canonical timezone for JVM and PostgreSQL communication.
		System.setProperty("user.timezone", "UTC");
		SpringApplication.run(BackendApplication.class, args);
	}

}
