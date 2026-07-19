package com.petstore.core.catalog.web;

public record ProductResponse(String productId, String categoryId, String name, String image, String description) {
}
