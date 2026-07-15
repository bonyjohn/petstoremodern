package com.petstore.core.migration.customer;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.petstore.core.customer.document.CustomerDocument;
import com.petstore.core.migration.customer.LegacyCustomerParser.Result;

/**
 * Characterizes the legacy {@code Populate-UTF8.xml} Users+Customers -> Mongo document
 * parsing against the real seed data (no Mongo needed): 4 users, 4 customers, all
 * password {@code j2ee}, every customer joined to a user and hashed.
 */
class LegacyCustomerParserTest {

	private static final PasswordEncoder ENCODER = new BCryptPasswordEncoder();

	private static Result parseRealCustomers() {
		LegacyCustomerParser parser = new LegacyCustomerParser(ENCODER);
		try (InputStream xml = LegacyCustomerParserTest.class.getResourceAsStream("/migration/Populate-UTF8.xml")) {
			return parser.parse(xml);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	void convertsAllFourCustomersFromRealSeedDataWithNoneDropped() {
		Result result = parseRealCustomers();

		assertThat(result.customers()).hasSize(4);
		assertThat(result.customers())
				.extracting(CustomerDocument::id)
				.containsExactlyInAnyOrder("j2ee", "j2ee-ja", "shopper", "j2ee-zh");
		assertThat(result.stats().customersConverted()).isEqualTo(4);
		assertThat(result.stats().customersDroppedNoUser()).isZero();
	}

	@Test
	void passwordIsJoinedFromTheUserAndHashedNeverStoredAsPlaintext() {
		CustomerDocument j2ee = findCustomer(parseRealCustomers(), "j2ee");

		assertThat(j2ee.passwordHash()).isNotEqualTo("j2ee");
		assertThat(ENCODER.matches("j2ee", j2ee.passwordHash())).isTrue();
	}

	@Test
	void everyMigratedCustomerGetsTheUserRole() {
		Result result = parseRealCustomers();

		assertThat(result.customers()).allSatisfy(c -> assertThat(c.roles()).containsExactly("USER"));
	}

	@Test
	void shopperCarriesATwoLineStreetAddressAndItsFullAggregate() {
		CustomerDocument shopper = findCustomer(parseRealCustomers(), "shopper");

		assertThat(shopper.account().contactInfo().address().street())
				.containsExactly("1234 Anywhere Street", "Unit 555");
		assertThat(shopper.account().contactInfo().address().city()).isEqualTo("Palo Alto");
		assertThat(shopper.account().contactInfo().address().state()).isEqualTo("CA");
		assertThat(shopper.account().contactInfo().address().zipCode()).isEqualTo("94303");
		assertThat(shopper.account().contactInfo().address().country()).isEqualTo("USA");
		assertThat(shopper.account().contactInfo().email()).isEqualTo("aaa@bbb.ccc");
		assertThat(shopper.account().creditCard().cardNumber()).isEqualTo("123456789");
		assertThat(shopper.account().creditCard().cardType()).isEqualTo("Meow Card");
		assertThat(shopper.profile().preferredLanguage()).isEqualTo("en_US");
		assertThat(shopper.profile().favoriteCategory()).isEqualTo("REPTILES");
		assertThat(shopper.profile().myListPreference()).isTrue();
		assertThat(shopper.profile().bannerPreference()).isFalse();
	}

	@Test
	void j2eeJaCarriesUtf8NameAndAddressVerbatim() {
		CustomerDocument j2eeJa = findCustomer(parseRealCustomers(), "j2ee-ja");

		assertThat(j2eeJa.account().contactInfo().familyName()).isEqualTo("富士");
		assertThat(j2eeJa.account().contactInfo().givenName()).isEqualTo("政憲");
		assertThat(j2eeJa.account().contactInfo().address().street())
				.containsExactly("歌舞伎町1-3-5", "ジャバビル２３");
		assertThat(j2eeJa.account().contactInfo().address().city()).isEqualTo("東京都");
		assertThat(j2eeJa.account().contactInfo().address().country()).isEqualTo("日本");
		assertThat(j2eeJa.profile().preferredLanguage()).isEqualTo("ja_JP");
	}

	@Test
	void dropsAndCountsACustomerWithNoMatchingUser() {
		String xml = """
				<?xml version="1.0" encoding="UTF-8"?>
				<Populate>
				  <Users>
				    <User id="someoneelse"><Password>pw</Password></User>
				  </Users>
				  <Customers>
				    <Customer id="nobody">
				      <Account>
				        <ContactInfo>
				          <FamilyName>A</FamilyName>
				          <GivenName>B</GivenName>
				          <Address>
				            <StreetName>1 St</StreetName>
				            <City>City</City>
				            <State>ST</State>
				            <ZipCode>00000</ZipCode>
				            <Country>USA</Country>
				          </Address>
				          <Email>a@b.c</Email>
				          <Phone>555</Phone>
				        </ContactInfo>
				        <CreditCard>
				          <CardNumber>111</CardNumber>
				          <CardType>VISA</CardType>
				          <ExpiryDate>01/01</ExpiryDate>
				        </CreditCard>
				      </Account>
				      <Profile>
				        <PreferredLanguage>en_US</PreferredLanguage>
				        <FavoriteCategory>FISH</FavoriteCategory>
				        <MyListPreference>false</MyListPreference>
				        <BannerPreference>false</BannerPreference>
				      </Profile>
				    </Customer>
				  </Customers>
				</Populate>
				""";

		Result result = new LegacyCustomerParser(ENCODER)
				.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

		assertThat(result.customers()).isEmpty();
		assertThat(result.stats().customersConverted()).isZero();
		assertThat(result.stats().customersDroppedNoUser()).isEqualTo(1);
	}

	private CustomerDocument findCustomer(Result result, String id) {
		Optional<CustomerDocument> match = result.customers().stream()
				.filter(c -> c.id().equals(id))
				.findFirst();
		assertThat(match).as("customer " + id).isPresent();
		return match.get();
	}
}
