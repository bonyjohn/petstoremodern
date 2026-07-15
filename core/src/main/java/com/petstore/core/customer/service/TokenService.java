package com.petstore.core.customer.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Issues signed (HS256) JWTs: subject = username, claim {@code roles} = the customer's roles. */
@Service
public class TokenService {

	private final JwtEncoder jwtEncoder;
	private final Duration ttl;

	public TokenService(JwtEncoder jwtEncoder, @Value("${petstore.jwt.ttl}") Duration ttl) {
		this.jwtEncoder = jwtEncoder;
		this.ttl = ttl;
	}

	public String issue(String username, List<String> roles) {
		Instant now = Instant.now();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.subject(username)
				.issuedAt(now)
				.expiresAt(now.plus(ttl))
				.claim("roles", roles)
				.build();
		return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}
}
