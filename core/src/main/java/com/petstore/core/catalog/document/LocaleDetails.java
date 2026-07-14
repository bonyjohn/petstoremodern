package com.petstore.core.catalog.document;

import java.util.Optional;

/**
 * The three locales the legacy catalog ships in, as fixed fields (not a map) so
 * each can carry its own {@code @TextIndexed} annotation for catalog search.
 */
public record LocaleDetails(LocaleDetail en_US, LocaleDetail ja_JP, LocaleDetail zh_CN) {

	public static final String DEFAULT_LOCALE = "en_US";

	/** Returns the requested locale's detail, falling back to en_US. */
	public Optional<LocaleDetail> forLocale(String locale) {
		LocaleDetail detail = switch (locale) {
			case "ja_JP" -> ja_JP;
			case "zh_CN" -> zh_CN;
			default -> en_US;
		};
		return Optional.ofNullable(detail).or(() -> Optional.ofNullable(en_US));
	}
}
