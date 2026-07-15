package com.petstore.core.customer.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.petstore.core.customer.document.Account;
import com.petstore.core.customer.document.Address;
import com.petstore.core.customer.document.ContactInfo;
import com.petstore.core.customer.document.CreditCard;
import com.petstore.core.customer.document.CustomerDocument;
import com.petstore.core.customer.document.Profile;
import com.petstore.core.customer.repository.CustomerRepository;
import com.petstore.core.customer.web.AccountDto;
import com.petstore.core.customer.web.AddressDto;
import com.petstore.core.customer.web.ContactInfoDto;
import com.petstore.core.customer.web.CreditCardDto;
import com.petstore.core.customer.web.CustomerResponse;
import com.petstore.core.customer.web.CustomerUpdateRequest;
import com.petstore.core.customer.web.LoginRequest;
import com.petstore.core.customer.web.LoginResponse;
import com.petstore.core.customer.web.ProfileDto;
import com.petstore.core.customer.web.SignupRequest;

/**
 * Signup/login/profile for customers. Shapes {@link CustomerDocument} into the REST
 * responses (mirroring {@code CatalogService}) and is the only place {@code passwordHash}
 * is read or written — it never leaves this class in a response.
 */
@Service
public class CustomerService {

	private final CustomerRepository customerRepository;
	private final PasswordEncoder passwordEncoder;
	private final TokenService tokenService;

	public CustomerService(CustomerRepository customerRepository, PasswordEncoder passwordEncoder,
			TokenService tokenService) {
		this.customerRepository = customerRepository;
		this.passwordEncoder = passwordEncoder;
		this.tokenService = tokenService;
	}

	public LoginResponse signup(SignupRequest request) {
		if (customerRepository.existsById(request.username())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "username already taken");
		}

		ContactInfo contactInfo = new ContactInfo(request.familyName(), request.givenName(), null, request.email(), null);
		Account account = new Account(contactInfo, null);
		Profile profile = new Profile(null, null, false, false);
		List<String> roles = List.of("USER");

		CustomerDocument customer = new CustomerDocument(
				request.username(), passwordEncoder.encode(request.password()), roles, account, profile);
		customerRepository.save(customer);

		return new LoginResponse(tokenService.issue(customer.id(), roles), customer.id(), roles);
	}

	public LoginResponse login(LoginRequest request) {
		CustomerDocument customer = customerRepository.findById(request.username())
				.filter(c -> passwordEncoder.matches(request.password(), c.passwordHash()))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "bad credentials"));

		return new LoginResponse(tokenService.issue(customer.id(), customer.roles()), customer.id(), customer.roles());
	}

	public CustomerResponse getMe(String username) {
		return toResponse(findOrThrow(username));
	}

	public CustomerResponse updateMe(String username, CustomerUpdateRequest request) {
		CustomerDocument existing = findOrThrow(username);
		CustomerDocument updated = new CustomerDocument(
				existing.id(), existing.passwordHash(), existing.roles(),
				toAccount(request.account()), toProfile(request.profile()));
		return toResponse(customerRepository.save(updated));
	}

	private CustomerDocument findOrThrow(String username) {
		return customerRepository.findById(username)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no such customer"));
	}

	private CustomerResponse toResponse(CustomerDocument customer) {
		return new CustomerResponse(customer.id(), customer.roles(), toAccountDto(customer.account()), toProfileDto(customer.profile()));
	}

	private AccountDto toAccountDto(Account account) {
		if (account == null) {
			return null;
		}
		return new AccountDto(toContactInfoDto(account.contactInfo()), toCreditCardDto(account.creditCard()));
	}

	private ContactInfoDto toContactInfoDto(ContactInfo contactInfo) {
		if (contactInfo == null) {
			return null;
		}
		return new ContactInfoDto(contactInfo.familyName(), contactInfo.givenName(),
				toAddressDto(contactInfo.address()), contactInfo.email(), contactInfo.phone());
	}

	private AddressDto toAddressDto(Address address) {
		if (address == null) {
			return null;
		}
		return new AddressDto(address.street(), address.city(), address.state(), address.zipCode(), address.country());
	}

	private CreditCardDto toCreditCardDto(CreditCard creditCard) {
		if (creditCard == null) {
			return null;
		}
		return new CreditCardDto(creditCard.cardNumber(), creditCard.cardType(), creditCard.expiryDate());
	}

	private ProfileDto toProfileDto(Profile profile) {
		if (profile == null) {
			return null;
		}
		return new ProfileDto(profile.preferredLanguage(), profile.favoriteCategory(),
				profile.myListPreference(), profile.bannerPreference());
	}

	private Account toAccount(AccountDto dto) {
		return new Account(toContactInfo(dto.contactInfo()), toCreditCard(dto.creditCard()));
	}

	private ContactInfo toContactInfo(ContactInfoDto dto) {
		if (dto == null) {
			return null;
		}
		return new ContactInfo(dto.familyName(), dto.givenName(), toAddress(dto.address()), dto.email(), dto.phone());
	}

	private Address toAddress(AddressDto dto) {
		if (dto == null) {
			return null;
		}
		return new Address(dto.street(), dto.city(), dto.state(), dto.zipCode(), dto.country());
	}

	private CreditCard toCreditCard(CreditCardDto dto) {
		if (dto == null) {
			return null;
		}
		return new CreditCard(dto.cardNumber(), dto.cardType(), dto.expiryDate());
	}

	private Profile toProfile(ProfileDto dto) {
		return new Profile(dto.preferredLanguage(), dto.favoriteCategory(), dto.myListPreference(), dto.bannerPreference());
	}
}
