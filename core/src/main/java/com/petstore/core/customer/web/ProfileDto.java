package com.petstore.core.customer.web;

/** Wire shape for {@link com.petstore.core.customer.document.Profile}. */
public record ProfileDto(String preferredLanguage, String favoriteCategory, boolean myListPreference, boolean bannerPreference) {
}
