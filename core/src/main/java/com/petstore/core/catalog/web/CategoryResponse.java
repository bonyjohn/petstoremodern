package com.petstore.core.catalog.web;

/** A category, localized to the requested locale (falls back to en_US). */
public record CategoryResponse(String id, String name, String image) {
}
