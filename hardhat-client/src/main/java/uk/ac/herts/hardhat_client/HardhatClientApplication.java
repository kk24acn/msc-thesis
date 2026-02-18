package uk.ac.herts.hardhat_client;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import uk.ac.herts.hardhat_client.service.HardhatService;

import java.math.BigDecimal;

@SpringBootApplication
public class HardhatClientApplication {

	public static void main(String[] args) {
		SpringApplication.run(HardhatClientApplication.class, args);

		HardhatService svc = new HardhatService();
		try {
			svc.sendEther("0x70997970c51812dc3a010c7d01b50e0d17dc79c8", new BigDecimal(25));
		} catch(Exception e) {
			System.out.println("Something went wrong");
		}
	}

}
