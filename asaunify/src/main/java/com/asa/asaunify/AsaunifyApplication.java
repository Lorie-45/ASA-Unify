package com.asa.asaunify;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class AsaunifyApplication {

	public static void main(String[] args) {
		SpringApplication.run(AsaunifyApplication.class, args);
	}

}
