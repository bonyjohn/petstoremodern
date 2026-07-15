package com.petstore.fulfillment.inventory;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/** Admin-only stock management (ROLE_ADMIN enforced in the security config). */
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

	private final InventoryRepository inventoryRepository;

	public InventoryController(InventoryRepository inventoryRepository) {
		this.inventoryRepository = inventoryRepository;
	}

	@GetMapping
	public List<InventoryDocument> inventory() {
		return inventoryRepository.findAll();
	}

	@PutMapping("/{id}")
	public InventoryDocument update(@PathVariable String id, @Valid @RequestBody InventoryUpdateRequest request) {
		return inventoryRepository.save(new InventoryDocument(id, request.quantityOnHand()));
	}
}
