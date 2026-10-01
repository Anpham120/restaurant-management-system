package vn.khoibep.rms;

import org.springframework.boot.SpringApplication;

public class TestRmsApplication {

	public static void main(String[] args) {
		SpringApplication.from(RmsApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
