package com.petstore.core.catalog.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** One legacy {@code <Category>} — {@code _id} is the legacy category id (e.g. {@code DOGS}). */
@Document("categories")
public record CategoryDocument(@Id String id, LocaleDetails details) {
}
