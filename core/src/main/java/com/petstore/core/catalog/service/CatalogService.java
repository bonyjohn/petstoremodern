package com.petstore.core.catalog.service;

import java.util.List;
import java.util.Optional;

import org.springframework.data.annotation.Id;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

import com.petstore.core.catalog.document.CategoryDocument;
import com.petstore.core.catalog.document.LocaleDetail;
import com.petstore.core.catalog.document.LocaleDetails;
import com.petstore.core.catalog.document.ProductDocument;
import com.petstore.core.catalog.repository.CategoryRepository;
import com.petstore.core.catalog.repository.ProductRepository;
import com.petstore.core.catalog.web.CategoryResponse;
import com.petstore.core.catalog.web.ItemResponse;
import com.petstore.core.catalog.web.ProductResponse;
import com.petstore.core.catalog.web.ProductSummaryResponse;

/**
 * Localizes catalog documents to the requested locale (falling back to en_US)
 * and shapes them into the REST responses. Items don't carry their product's
 * name, so item reads join {@code items} to {@code products} with a MongoDB
 * {@code $lookup} aggregation. The catalog is small (5 categories, 16 products,
 * 28 items) so a single page comfortably covers every list.
 */
@Service
public class CatalogService {

	private static final LocaleDetail EMPTY_DETAIL = new LocaleDetail(null, null, null, null, null, null);
	private static final PageRequest FULL_CATALOG_PAGE = PageRequest.of(0, 200);

	private final CategoryRepository categoryRepository;
	private final ProductRepository productRepository;
	private final MongoTemplate mongoTemplate;

	public CatalogService(CategoryRepository categoryRepository, ProductRepository productRepository,
			MongoTemplate mongoTemplate) {
		this.categoryRepository = categoryRepository;
		this.productRepository = productRepository;
		this.mongoTemplate = mongoTemplate;
	}

	public List<CategoryResponse> listCategories(String locale) {
		return categoryRepository.findAll().stream()
				.map(category -> toCategoryResponse(category, locale))
				.toList();
	}

	public List<ProductSummaryResponse> listProductsInCategory(String categoryId, String locale) {
		return productRepository.findByCategoryId(categoryId, FULL_CATALOG_PAGE).stream()
				.map(product -> toProductSummary(product, locale))
				.toList();
	}

	public Optional<ProductResponse> getProduct(String productId, String locale) {
		return productRepository.findById(productId).map(product -> toProductResponse(product, locale));
	}

	public List<ItemResponse> listItemsForProduct(String productId, String locale) {
		return findItemsWithProduct(Criteria.where("productId").is(productId)).stream()
				.map(row -> toItemResponse(row, locale))
				.toList();
	}

	public Optional<ItemResponse> getItem(String itemId, String locale) {
		return findItemsWithProduct(Criteria.where("_id").is(itemId)).stream()
				.findFirst()
				.map(row -> toItemResponse(row, locale));
	}

	public List<ProductSummaryResponse> search(String query, String locale) {
		return productRepository.search(query, FULL_CATALOG_PAGE).stream()
				.map(product -> toProductSummary(product, locale))
				.toList();
	}

	/**
	 * Joins {@code items} to their {@code products} document via {@code $lookup}
	 * (local {@code productId} to foreign {@code _id}), so an item read can carry
	 * its product's name without embedding the product in every item.
	 */
	private List<ItemWithProduct> findItemsWithProduct(Criteria itemCriteria) {
		Aggregation aggregation = Aggregation.newAggregation(
				Aggregation.match(itemCriteria),
				Aggregation.lookup("products", "productId", "_id", "product"),
				Aggregation.unwind("product"));
		AggregationResults<ItemWithProduct> results =
				mongoTemplate.aggregate(aggregation, "items", ItemWithProduct.class);
		return results.getMappedResults();
	}

	/** Aggregation projection: an item document plus its joined product. */
	private record ItemWithProduct(@Id String id, String productId, LocaleDetails details, ProductDocument product) {
	}

	private CategoryResponse toCategoryResponse(CategoryDocument category, String locale) {
		LocaleDetail detail = category.details().forLocale(locale).orElse(EMPTY_DETAIL);
		return new CategoryResponse(category.id(), detail.name(), detail.image());
	}

	private ProductSummaryResponse toProductSummary(ProductDocument product, String locale) {
		LocaleDetail detail = localeDetail(product.details(), locale);
		return new ProductSummaryResponse(product.id(), detail.name(), detail.image(), detail.description());
	}

	private ProductResponse toProductResponse(ProductDocument product, String locale) {
		LocaleDetail detail = localeDetail(product.details(), locale);
		return new ProductResponse(product.id(), product.categoryId(), detail.name(), detail.image(), detail.description());
	}

	private ItemResponse toItemResponse(ItemWithProduct row, String locale) {
		LocaleDetail itemDetail = localeDetail(row.details(), locale);
		LocaleDetail productDetail = localeDetail(row.product().details(), locale);
		return new ItemResponse(
				row.id(), row.productId(), productDetail.name(),
				itemDetail.description(), itemDetail.image(), itemDetail.listPrice(), itemDetail.attributes());
	}

	private LocaleDetail localeDetail(LocaleDetails details, String locale) {
		return details.forLocale(locale).orElse(EMPTY_DETAIL);
	}
}
