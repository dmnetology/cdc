package com.example.demo;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

/**
 * Веб-сервис сбора контактных данных с сайтов организаций.
 * Демонстрационный краулинг запускается по расписанию, вручную задание заводится по POST /crawl.
 */
@SpringBootApplication
@EnableScheduling
public class DemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}

	/** HTTP-клиент скачивания страниц с таймаутами соединения и чтения. */
	@Bean
	public RestTemplate restTemplate() {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(3_000);
		factory.setReadTimeout(5_000);
		return new RestTemplate(factory);
	}

	/** Справка по эндпоинтам, печатается в консоль при старте. */
	@Bean
	public CommandLineRunner welcomeRunner() {
		return args -> {
			System.out.println();
			System.out.println("=== Итоговое задание. Многопоточный веб-краулер контактных данных ===");
			System.out.println("POST /crawl                                запуск задания (JSON со списком стартовых URL либо пустое тело)");
			System.out.println("GET  /answer?page=&size=&sort=&order=      собранные контакты, сортировка через parallelStream");
			System.out.println("GET  /status/{taskId}                      статистика задания");
			System.out.println("Через 10 секунд планировщик сам запустит демонстрационный краулинг встроенного источника");
		};
	}
}
