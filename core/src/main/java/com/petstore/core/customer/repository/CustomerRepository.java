package com.petstore.core.customer.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.petstore.core.customer.document.CustomerDocument;

public interface CustomerRepository extends MongoRepository<CustomerDocument, String> {
}
