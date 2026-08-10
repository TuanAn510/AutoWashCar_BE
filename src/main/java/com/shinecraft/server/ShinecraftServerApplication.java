package com.shinecraft.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class ShinecraftServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(ShinecraftServerApplication.class, args);
	}

}
