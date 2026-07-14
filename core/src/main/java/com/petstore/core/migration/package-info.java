/**
 * Data-migration code: everything that understands the legacy Pet Store 1.3.1
 * formats (Populate-UTF8.xml parsing, conversion to the Mongo document model,
 * seeding). Deliberately segregated from the application modules with a one-way
 * dependency rule enforced by ArchUnit: migration code may use the modules'
 * documents and repositories, but no application code may ever depend on this
 * package. Retiring the legacy system means deleting this package — nothing else.
 */
package com.petstore.core.migration;
