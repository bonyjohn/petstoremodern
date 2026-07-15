package com.petstore.core.customer.web;

import java.util.List;

public record LoginResponse(String token, String username, List<String> roles) {
}
