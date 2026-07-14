package com.petstore.core.migration.catalog;

import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexDefinition;
import org.springframework.data.mongodb.core.index.TextIndexDefinition;
import org.springframework.stereotype.Component;

import com.petstore.core.catalog.document.ItemDocument;
import com.petstore.core.catalog.document.ProductDocument;
import com.petstore.core.catalog.repository.CategoryRepository;
import com.petstore.core.catalog.repository.ItemRepository;
import com.petstore.core.catalog.repository.ProductRepository;

/**
 * Loads the legacy {@code Populate-UTF8.xml} catalog into MongoDB. Gated behind
 * {@code petstore.seed=true} (default off) so it doesn't run on every startup.
 * Idempotent: {@code saveAll} upserts by {@code _id}, so re-running replaces
 * existing documents rather than duplicating them.
 */
@Component
@ConditionalOnProperty(name = "petstore.seed", havingValue = "true")
public class CatalogSeeder implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(CatalogSeeder.class);

	private final Resource legacyCatalogXml;
	private final CategoryRepository categoryRepository;
	private final ProductRepository productRepository;
	private final ItemRepository itemRepository;
	private final MongoTemplate mongoTemplate;

	public CatalogSeeder(
			@Value("classpath:migration/Populate-UTF8.xml") Resource legacyCatalogXml,
			CategoryRepository categoryRepository,
			ProductRepository productRepository,
			ItemRepository itemRepository,
			MongoTemplate mongoTemplate) {
		this.legacyCatalogXml = legacyCatalogXml;
		this.categoryRepository = categoryRepository;
		this.productRepository = productRepository;
		this.itemRepository = itemRepository;
		this.mongoTemplate = mongoTemplate;
	}

	@Override
	public void run(ApplicationArguments args) throws Exception {
		try (InputStream xml = legacyCatalogXml.getInputStream()) {
			LegacyCatalogParser.Result result = new LegacyCatalogParser().parse(xml);

			categoryRepository.saveAll(result.categories());
			productRepository.saveAll(result.products());
			itemRepository.saveAll(result.items());
			ensureIndexes();

			log.info("Catalog seeded: {} categories, {} products, {} items, "
							+ "{} items dropped (no matching product), "
							+ "{} item-locales dropped (missing ItemDetails)",
					result.categories().size(), result.products().size(), result.items().size(),
					result.stats().itemsDroppedNoProduct(), result.stats().itemLocalesDropped());
		}
	}

	private void ensureIndexes() {
		mongoTemplate.indexOps(ProductDocument.class).createIndex(new Index("categoryId", Direction.ASC));
		mongoTemplate.indexOps(ItemDocument.class).createIndex(new Index("productId", Direction.ASC));

		IndexDefinition textIndex = TextIndexDefinition.builder()
				.onField("details.en_US.name")
				.onField("details.en_US.description")
				.onField("details.ja_JP.name")
				.onField("details.ja_JP.description")
				.onField("details.zh_CN.name")
				.onField("details.zh_CN.description")
				.build();
		mongoTemplate.indexOps(ProductDocument.class).createIndex(textIndex);
	}
}
