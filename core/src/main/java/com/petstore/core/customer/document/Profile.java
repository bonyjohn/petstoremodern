package com.petstore.core.customer.document;

/** The legacy {@code <Profile>}: display/shopping preferences. */
public record Profile(String preferredLanguage, String favoriteCategory, boolean myListPreference, boolean bannerPreference) {
}
