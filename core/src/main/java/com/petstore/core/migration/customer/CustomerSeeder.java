package com.petstore.core.migration.customer;

import java.io.InputStream;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.petstore.core.customer.document.Account;
import com.petstore.core.customer.document.ContactInfo;
import com.petstore.core.customer.document.CustomerDocument;
import com.petstore.core.customer.document.Profile;
import com.petstore.core.customer.repository.CustomerRepository;

/**
 * Loads the legacy {@code Populate-UTF8.xml} Users+Customers into MongoDB, plus one
 * admin account. Gated behind {@code petstore.seed=true} (default off), same as
 * {@code CatalogSeeder}. Idempotent: {@code saveAll}/{@code save} upsert by {@code _id},
 * so re-running replaces existing documents (including re-hashing passwords) rather than
 * duplicating them.
 * <p>
 * Legacy Pet Store had a separate admin webapp ({@code jps_admin}) with its own user
 * store; the modern app is a single API with role-based access, so the admin becomes
 * just another customer document with an extra role.
 */
@Component
@ConditionalOnProperty(name = "petstore.seed", havingValue = "true")
public class CustomerSeeder implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(CustomerSeeder.class);

	private final Resource legacyCustomerXml;
	private final CustomerRepository customerRepository;
	private final PasswordEncoder passwordEncoder;
	private final String adminPassword;

	public CustomerSeeder(
			@Value("classpath:migration/Populate-UTF8.xml") Resource legacyCustomerXml,
			CustomerRepository customerRepository,
			PasswordEncoder passwordEncoder,
			@Value("${petstore.seed.admin-password}") String adminPassword) {
		this.legacyCustomerXml = legacyCustomerXml;
		this.customerRepository = customerRepository;
		this.passwordEncoder = passwordEncoder;
		this.adminPassword = adminPassword;
	}

	@Override
	public void run(ApplicationArguments args) throws Exception {
		try (InputStream xml = legacyCustomerXml.getInputStream()) {
			LegacyCustomerParser.Result result = new LegacyCustomerParser(passwordEncoder).parse(xml);

			customerRepository.saveAll(result.customers());
			seedAdmin();

			log.info("Customers seeded: {} customers, {} dropped (no matching user password)",
					result.stats().customersConverted(), result.stats().customersDroppedNoUser());
		}
	}

	private void seedAdmin() {
		ContactInfo contactInfo = new ContactInfo("Administrator", "Petstore", null, "admin@petstore.example", null);
		Account account = new Account(contactInfo, null);
		Profile profile = new Profile("en_US", null, false, false);
		CustomerDocument admin = new CustomerDocument(
				"admin", passwordEncoder.encode(adminPassword), List.of("USER", "ADMIN"), account, profile);
		customerRepository.save(admin);
	}
}
