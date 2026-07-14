package com.petstore.core.migration.catalog;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import com.petstore.core.catalog.document.CategoryDocument;
import com.petstore.core.catalog.document.ItemDocument;
import com.petstore.core.catalog.document.LocaleDetail;
import com.petstore.core.catalog.document.LocaleDetails;
import com.petstore.core.catalog.document.ProductDocument;

/**
 * Parses the {@code <Catalog>} portion of the legacy {@code Populate-UTF8.xml}
 * (Categories/Products/Items, each with per-locale {@code *Details} children)
 * directly into the app's document model, using plain StAX. DTD loading and
 * external entities are disabled so the file parses standalone, without the
 * {@code dtds/} directory it references.
 */
public final class LegacyCatalogParser {

	public record Result(
			List<CategoryDocument> categories,
			List<ProductDocument> products,
			List<ItemDocument> items,
			MigrationStats stats) {
	}

	public Result parse(InputStream xml) {
		try {
			XMLInputFactory factory = XMLInputFactory.newInstance();
			factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
			factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);

			XMLStreamReader reader = factory.createXMLStreamReader(xml);
			try {
				return parseCatalog(reader);
			} finally {
				reader.close();
			}
		} catch (XMLStreamException e) {
			throw new IllegalStateException("Failed to parse legacy catalog XML", e);
		}
	}

	private Result parseCatalog(XMLStreamReader reader) throws XMLStreamException {
		List<CategoryDocument> categories = new ArrayList<>();
		List<ProductDocument> products = new ArrayList<>();
		List<ItemDocument> items = new ArrayList<>();
		Set<String> productIds = new HashSet<>();
		int itemsDroppedNoProduct = 0;
		int itemLocalesDropped = 0;

		while (reader.hasNext()) {
			int event = reader.next();
			if (event != XMLStreamReader.START_ELEMENT) {
				continue;
			}
			switch (reader.getLocalName()) {
				case "Category" -> categories.add(parseCategory(reader));
				case "Product" -> {
					ProductDocument product = parseProduct(reader);
					products.add(product);
					productIds.add(product.id());
				}
				case "Item" -> {
					ItemDocument item = parseItem(reader);
					if (!productIds.contains(item.productId())) {
						// An <Item product="..."> that matches no parsed <Product> has nothing
						// to join to at read time, so it can't be represented. The DTD
						// guarantees Products precede Items, so productIds is fully populated
						// by the time any Item is seen.
						itemsDroppedNoProduct++;
					} else {
						items.add(item);
						itemLocalesDropped += missingLocaleCount(item.details());
					}
				}
				default -> {
					// Users/Customers and other elements outside <Catalog> are not needed here.
				}
			}
		}

		MigrationStats stats = new MigrationStats(
				categories.size(), products.size(), items.size(), itemsDroppedNoProduct, itemLocalesDropped);
		return new Result(categories, products, items, stats);
	}

	private int missingLocaleCount(LocaleDetails details) {
		int missing = 0;
		if (details.en_US() == null) {
			missing++;
		}
		if (details.ja_JP() == null) {
			missing++;
		}
		if (details.zh_CN() == null) {
			missing++;
		}
		return missing;
	}

	private CategoryDocument parseCategory(XMLStreamReader reader) throws XMLStreamException {
		String id = reader.getAttributeValue(null, "id");
		Map<String, LocaleDetail> details = new LinkedHashMap<>();

		while (!(reader.isEndElement() && reader.getLocalName().equals("Category"))) {
			reader.next();
			if (reader.isStartElement() && reader.getLocalName().equals("CategoryDetails")) {
				String locale = normalizeLocale(reader.getAttributeValue(
						"http://www.w3.org/XML/1998/namespace", "lang"));
				String name = null;
				String image = null;
				while (!(reader.isEndElement() && reader.getLocalName().equals("CategoryDetails"))) {
					reader.next();
					if (reader.isStartElement()) {
						switch (reader.getLocalName()) {
							case "Name" -> name = reader.getElementText();
							case "Image" -> image = reader.getElementText();
							default -> {
							}
						}
					}
				}
				details.put(locale, new LocaleDetail(name, null, image, null, null, null));
			}
		}

		return new CategoryDocument(id, toLocaleDetails(details));
	}

	private ProductDocument parseProduct(XMLStreamReader reader) throws XMLStreamException {
		String id = reader.getAttributeValue(null, "id");
		String categoryId = reader.getAttributeValue(null, "category");
		Map<String, LocaleDetail> details = new LinkedHashMap<>();

		while (!(reader.isEndElement() && reader.getLocalName().equals("Product"))) {
			reader.next();
			if (reader.isStartElement() && reader.getLocalName().equals("ProductDetails")) {
				String locale = normalizeLocale(reader.getAttributeValue(
						"http://www.w3.org/XML/1998/namespace", "lang"));
				String name = null;
				String image = null;
				String description = null;
				while (!(reader.isEndElement() && reader.getLocalName().equals("ProductDetails"))) {
					reader.next();
					if (reader.isStartElement()) {
						switch (reader.getLocalName()) {
							case "Name" -> name = reader.getElementText();
							case "Image" -> image = reader.getElementText();
							case "Description" -> description = reader.getElementText();
							default -> {
							}
						}
					}
				}
				details.put(locale, new LocaleDetail(name, description, image, null, null, null));
			}
		}

		return new ProductDocument(id, categoryId, toLocaleDetails(details));
	}

	private ItemDocument parseItem(XMLStreamReader reader) throws XMLStreamException {
		String id = reader.getAttributeValue(null, "id");
		String productId = reader.getAttributeValue(null, "product");
		Map<String, LocaleDetail> details = new LinkedHashMap<>();

		while (!(reader.isEndElement() && reader.getLocalName().equals("Item"))) {
			reader.next();
			if (reader.isStartElement() && reader.getLocalName().equals("ItemDetails")) {
				String locale = normalizeLocale(reader.getAttributeValue(
						"http://www.w3.org/XML/1998/namespace", "lang"));
				BigDecimal listPrice = null;
				BigDecimal unitCost = null;
				List<String> attributes = new ArrayList<>();
				String image = null;
				String description = null;
				while (!(reader.isEndElement() && reader.getLocalName().equals("ItemDetails"))) {
					reader.next();
					if (reader.isStartElement()) {
						switch (reader.getLocalName()) {
							case "ListPrice" -> listPrice = new BigDecimal(reader.getElementText().trim());
							case "UnitCost" -> unitCost = new BigDecimal(reader.getElementText().trim());
							case "Attribute" -> attributes.add(reader.getElementText());
							case "Image" -> image = reader.getElementText();
							case "Description" -> description = reader.getElementText();
							default -> {
							}
						}
					}
				}
				details.put(locale, new LocaleDetail(null, description, image, listPrice, unitCost, attributes));
			}
		}

		return new ItemDocument(id, productId, toLocaleDetails(details));
	}

	private LocaleDetails toLocaleDetails(Map<String, LocaleDetail> details) {
		return new LocaleDetails(details.get("en_US"), details.get("ja_JP"), details.get("zh_CN"));
	}

	/** {@code en-US} (xml:lang, hyphenated per BCP 47) -&gt; {@code en_US} (the target document model's key). */
	private String normalizeLocale(String xmlLang) {
		return xmlLang == null ? null : xmlLang.replace('-', '_');
	}
}
