package com.harems.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication
public class HaremsApiApplication {

	public static void main(String[] args) {
		log.info("Starting HaremsApiApplication");
		SpringApplication.run(HaremsApiApplication.class, args);
		log.info("HaremsApiApplication started successfully");
	}

}
