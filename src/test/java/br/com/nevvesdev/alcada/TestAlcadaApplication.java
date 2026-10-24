package br.com.nevvesdev.alcada;

import org.springframework.boot.SpringApplication;

public class TestAlcadaApplication {

	public static void main(String[] args) {
		SpringApplication.from(AlcadaApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
