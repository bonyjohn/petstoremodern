package com.petstore.core.catalog.web;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.petstore.core.catalog.service.CatalogService;

/** Browse/search endpoints for the catalog (see DEMO_FLOW.md's browse flow). */
@RestController
@RequestMapping("/api/catalog")
public class CatalogController {

	private final CatalogService catalogService;

	public CatalogController(CatalogService catalogService) {
		this.catalogService = catalogService;
	}

	@GetMapping("/categories")
	public List<CategoryResponse> categories(@RequestParam(defaultValue = "en_US") String locale) {
		return catalogService.listCategories(locale);
	}

	@GetMapping("/categories/{id}/products")
	public List<ProductSummaryResponse> productsInCategory(
			@PathVariable String id, @RequestParam(defaultValue = "en_US") String locale) {
		return catalogService.listProductsInCategory(id, locale);
	}

	@GetMapping("/products/{productId}")
	public ResponseEntity<ProductResponse> product(
			@PathVariable String productId, @RequestParam(defaultValue = "en_US") String locale) {
		return catalogService.getProduct(productId, locale)
				.map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

	@GetMapping("/products/{productId}/items")
	public List<ItemResponse> itemsForProduct(
			@PathVariable String productId, @RequestParam(defaultValue = "en_US") String locale) {
		return catalogService.listItemsForProduct(productId, locale);
	}

	@GetMapping("/items/{itemId}")
	public ResponseEntity<ItemResponse> item(
			@PathVariable String itemId, @RequestParam(defaultValue = "en_US") String locale) {
		return catalogService.getItem(itemId, locale)
				.map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

	@GetMapping("/search")
	public List<ProductSummaryResponse> search(
			@RequestParam String q, @RequestParam(defaultValue = "en_US") String locale) {
		return catalogService.search(q, locale);
	}
}
