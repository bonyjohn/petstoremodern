package com.petstore.core.migration.customer;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.springframework.security.crypto.password.PasswordEncoder;

import com.petstore.core.customer.document.Account;
import com.petstore.core.customer.document.Address;
import com.petstore.core.customer.document.ContactInfo;
import com.petstore.core.customer.document.CreditCard;
import com.petstore.core.customer.document.CustomerDocument;
import com.petstore.core.customer.document.Profile;

/**
 * Parses the {@code <Users>} and {@code <Customers>} portions of the legacy
 * {@code Populate-UTF8.xml} directly into the app's document model, using plain
 * StAX (DTD/external entities disabled, same as {@code LegacyCatalogParser}).
 * Users fill a plain id-&gt;password map; each Customer then looks up its
 * password there, collapsing the legacy six-entity aggregate into one
 * {@code CustomerDocument} in a single pass — the DTD guarantees Users precede
 * Customers, so the map is fully populated by the time any Customer is seen.
 * A Customer with no matching User has no password to migrate and is dropped
 * (counted in stats).
 * <p>
 * The legacy stores passwords in plaintext ({@code <Password>j2ee</Password>});
 * this is the one place that plaintext is ever read — it's hashed immediately
 * and never persisted or logged as-is.
 */
public final class LegacyCustomerParser {

	public record Result(List<CustomerDocument> customers, CustomerMigrationStats stats) {
	}

	private final PasswordEncoder passwordEncoder;

	public LegacyCustomerParser(PasswordEncoder passwordEncoder) {
		this.passwordEncoder = passwordEncoder;
	}

	public Result parse(InputStream xml) {
		try {
			XMLInputFactory factory = XMLInputFactory.newInstance();
			factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
			factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);

			XMLStreamReader reader = factory.createXMLStreamReader(xml);
			try {
				return parseUsersAndCustomers(reader);
			} finally {
				reader.close();
			}
		} catch (XMLStreamException e) {
			throw new IllegalStateException("Failed to parse legacy customer XML", e);
		}
	}

	private Result parseUsersAndCustomers(XMLStreamReader reader) throws XMLStreamException {
		Map<String, String> passwordsByUsername = new HashMap<>();
		List<CustomerDocument> customers = new ArrayList<>();
		int customersDroppedNoUser = 0;

		while (reader.hasNext()) {
			int event = reader.next();
			if (event != XMLStreamReader.START_ELEMENT) {
				continue;
			}
			switch (reader.getLocalName()) {
				case "User" -> parseUser(reader, passwordsByUsername);
				case "Customer" -> {
					CustomerDocument customer = parseCustomer(reader, passwordsByUsername);
					if (customer == null) {
						customersDroppedNoUser++;
					} else {
						customers.add(customer);
					}
				}
				default -> {
					// Catalog elements and other structure are not needed here.
				}
			}
		}

		return new Result(customers, new CustomerMigrationStats(customers.size(), customersDroppedNoUser));
	}

	private void parseUser(XMLStreamReader reader, Map<String, String> passwordsByUsername)
			throws XMLStreamException {
		String id = reader.getAttributeValue(null, "id");

		while (!(reader.isEndElement() && reader.getLocalName().equals("User"))) {
			reader.next();
			if (reader.isStartElement() && reader.getLocalName().equals("Password")) {
				passwordsByUsername.put(id, reader.getElementText());
			}
		}
	}

	/** Returns the finished document, or null when no {@code <User>} carried this customer's password. */
	private CustomerDocument parseCustomer(XMLStreamReader reader, Map<String, String> passwordsByUsername)
			throws XMLStreamException {
		String id = reader.getAttributeValue(null, "id");
		String familyName = null;
		String givenName = null;
		List<String> street = new ArrayList<>();
		String city = null;
		String state = null;
		String zipCode = null;
		String country = null;
		String email = null;
		String phone = null;
		String cardNumber = null;
		String cardType = null;
		String expiryDate = null;
		String preferredLanguage = null;
		String favoriteCategory = null;
		boolean myListPreference = false;
		boolean bannerPreference = false;

		while (!(reader.isEndElement() && reader.getLocalName().equals("Customer"))) {
			reader.next();
			if (!reader.isStartElement()) {
				continue;
			}
			switch (reader.getLocalName()) {
				case "FamilyName" -> familyName = reader.getElementText();
				case "GivenName" -> givenName = reader.getElementText();
				case "StreetName" -> street.add(reader.getElementText());
				case "City" -> city = reader.getElementText();
				case "State" -> state = reader.getElementText();
				case "ZipCode" -> zipCode = reader.getElementText();
				case "Country" -> country = reader.getElementText();
				case "Email" -> email = reader.getElementText();
				case "Phone" -> phone = reader.getElementText();
				case "CardNumber" -> cardNumber = reader.getElementText();
				case "CardType" -> cardType = reader.getElementText();
				case "ExpiryDate" -> expiryDate = reader.getElementText();
				case "PreferredLanguage" -> preferredLanguage = reader.getElementText();
				case "FavoriteCategory" -> favoriteCategory = reader.getElementText();
				case "MyListPreference" -> myListPreference = Boolean.parseBoolean(reader.getElementText().trim());
				case "BannerPreference" -> bannerPreference = Boolean.parseBoolean(reader.getElementText().trim());
				default -> {
				}
			}
		}

		String plaintextPassword = passwordsByUsername.get(id);
		if (plaintextPassword == null) {
			// No matching <User>, so no password to migrate; this customer can't log in
			// under the modern (password-required) auth model.
			return null;
		}

		Address address = new Address(street, city, state, zipCode, country);
		ContactInfo contactInfo = new ContactInfo(familyName, givenName, address, email, phone);
		CreditCard creditCard = new CreditCard(cardNumber, cardType, expiryDate);
		Account account = new Account(contactInfo, creditCard);
		Profile profile = new Profile(preferredLanguage, favoriteCategory, myListPreference, bannerPreference);

		return new CustomerDocument(
				id, passwordEncoder.encode(plaintextPassword), List.of("USER"), account, profile);
	}
}
