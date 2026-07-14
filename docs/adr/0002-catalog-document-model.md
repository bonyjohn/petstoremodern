# 2. Catalog document model

Date: 2026-07-14

## Status

Accepted

## Context

The legacy catalog spreads a sellable item across six tables: `category`,
`category_details` (one row per locale), `product`, `product_details` (one row
per locale), `item`, `item_attributes`. Browsing a category page joins across
several of them; the DAO layer builds the joins with hand-written SQL embedded
in an XML descriptor (`sql/*.xml`).

`Populate-UTF8.xml` — the legacy seed data — carries three locales per
category and product (`en-US`, `ja-JP`, `zh-CN`), and, unexpectedly, **three
independent price lists per item**: `ListPrice`/`UnitCost` are given separately
under each locale's `<ItemDetails>`, not converted from a single canonical
price (e.g. item `EST-1` lists `$16.50` under `en-US` and a *separate* `¥1951`
under `ja-JP` — not an FX conversion of the dollar price).

We need a Mongo document model for the `catalog` module (browse + search) that
is simple to query from the REST layer and preserves the locale data the
storefront needs.

This ADR originally embedded one `products` document per legacy **item**,
borrowing the item's name from its parent product's `ProductDetails` for that
same locale. Building the migration against the real seed data surfaced a
problem with that shape: `ProductDetails` and `ItemDetails` carry genuinely
different data at their own level, not just a shared name. Product `FI-SW-01`
is pictured as `fish1.jpg` and described as "Salt Water fish from Australia";
its item `EST-1` is pictured as `fish3.gif` and described as "Fresh Water fish
from Japan" — an embedded-item document that only borrows the product's name
silently drops the product's own image/description, which the storefront's
category page (a per-*product* listing, not per-item) actually needs.

## Decision

**Reference, not embed.** Three collections, each migrated verbatim from its
own legacy level, joined with `$lookup` where a read needs both levels:

```javascript
// categories — unchanged
{ _id: "FISH", details: { en_US: { name: "Fish", image: "fish_icon.gif" }, ja_JP: {...}, zh_CN: {...} } }

// products — one document per legacy <Product>, ProductDetails verbatim
{ _id: "FI-SW-01", categoryId: "FISH",
  details: {
    en_US: { name: "Angelfish", image: "fish1.jpg", description: "Salt Water fish from Australia" },
    ja_JP: { name: "エンゼルフィッシュ", image: "fish1.jpg", description: "オーストラリア産の海水魚" },
    zh_CN: { name: "天使鱼", image: "fish1.jpg", description: "澳大利亚产的海水鱼" }
  } }

// items — one document per legacy <Item>, ItemDetails verbatim; no name of its own
{ _id: "EST-1", productId: "FI-SW-01",
  details: {
    en_US: { image: "fish3.gif", description: "Fresh Water fish from Japan",
             listPrice: 16.50, unitCost: 10.00, attributes: ["Large", "Cuddly"] },
    ja_JP: { image: "fish3.gif", description: "日本産の淡水魚",
             listPrice: 1951, unitCost: 1551, attributes: ["大", "優しい"] },
    zh_CN: { image: "fish3.gif", description: "日本产的淡水鱼",
             listPrice: 142, unitCost: 86, attributes: ["大", "喜欢群居"] }
  } }
```

`categoryId` lives on the product only, mirroring where legacy actually has
it (`<Product category="...">`); items reference `productId` only. Products
leave `listPrice`/`unitCost`/`attributes` null; items leave `name` null —
`LocaleDetail` is shared shape, not shared meaning, across the two documents.

**Whole-block locale fallback, unchanged.** If a requested locale's block is
missing on a document, that document falls back to its own complete `en_US`
block — never mixing fields from two different locales *within one
document*. An item response naturally combines the item's own fields with its
joined product's name; that's the join, not locale mixing.

**Embed vs. reference: why reference wins here.** Product data has its own
lifecycle (a product's description/image can change independent of any one
item's price), and at real catalog size an embedded product block would be
duplicated once per item under that product (this seed data alone is 28 items
across only 16 products). Referencing costs a `$lookup` on the two read paths
that need an item's name (`GET /products/{id}/items`, `GET /items/{id}`); every
other read (`categories`, `categories/{id}/products`, `products/{id}`,
`search`) is a single-collection query, same as before.

**Per-locale prices, migrated verbatim, unchanged.** `Populate-UTF8.xml` gives
each item an independent price list under each locale's `<ItemDetails>` — e.g.
item `EST-1` lists `$16.50`/`$10.00` under `en-US` and a *separate*
`¥1951`/`¥1551` under `ja-JP`, not an FX conversion of the dollar price. We
migrate this verbatim: `listPrice`, `unitCost`, and `attributes` live inside
each locale's item detail block, not at the top level. There is no single
canonical price for an item — "the price" only means something once a locale
(or currency) is chosen. We chose faithful migration over inventing a
canonical price/currency the legacy data never actually had. Bare `BigDecimal`
numbers, no currency codes, matching the legacy schema — the frontend maps
locale → currency (`en_US`→USD, `ja_JP`→JPY, `zh_CN`→CNY) for display via
Angular's `CurrencyPipe`.

**Search.** A text index spans `details.en_US.name`, `details.en_US.description`,
`details.ja_JP.name`, `details.ja_JP.description`, `details.zh_CN.name`,
`details.zh_CN.description` on the **products** collection — one query
searches product names/descriptions across all three locales regardless of the
caller's selected locale, returning product summaries. Searching products and
clicking through to items matches the legacy storefront flow.

**Migration is tested code, segregated from the application.** All
legacy-facing code — `LegacyCatalogParser` (StAX, unit tested against the real
`Populate-UTF8.xml` on the classpath) and `CatalogSeeder` (an idempotent
`ApplicationRunner`, `saveAll` upserts by `_id`, gated on `petstore.seed=true`)
— lives in `com.petstore.core.migration.catalog`, **not** in the `catalog`
application module. The parser emits `CategoryDocument`/`ProductDocument`/
`ItemDocument` directly — there is no intermediate legacy model or separate
conversion step. That collapsed once the document model became one collection
migrated verbatim per legacy level (Category/Product/Item): with no
cross-level join or reshaping left to do, a `LegacyCatalog` + `CatalogConverter`
pair was a field-by-field copy of the parser's output, not a real
transformation. An intermediate legacy model earns its keep on slices with an
actual transform to isolate — e.g. the upcoming customer aggregate, which
merges several legacy tables into one document. The dependency is one-way and
ArchUnit-enforced
(`ModuleBoundariesTest.applicationCodeDoesNotDependOnMigration`): migration
code may use the modules' documents and repositories; application code may
never reference `migration..`. Retiring the legacy system is therefore a
package deletion. If migration code grows substantially (customer, order data
in later parts), the package can be extracted into its own Maven module
mechanically, because the one-way rule already holds.

## Consequences

- Browsing a category (`categoryId` on `products`) or a product's items
  (`productId` on `items`) is still a single indexed query per collection; an
  item's name additionally costs a `$lookup` to its product.
- Product-level data (image, description) is no longer silently lost — the
  category page and the new `GET /products/{id}` product-page header both read
  it directly from the `products` collection, not borrowed through an item.
- There is no single queryable "the price" for an item: cross-locale price
  queries or sorting (e.g. "cheapest item under $20") need a locale chosen
  first, since `$16.50`/`¥1951`/`¥142` for the same item aren't comparable
  without picking a currency. This is the correct reflection of what the
  legacy data actually is (three independent price lists per item), not a
  modeling shortcut.
- Part 4's order-approval threshold (legacy `PurchaseOrderMDB.canIApprove`:
  auto-approve under $500) becomes per-locale, mirroring the legacy code's own
  `<$500` (en-US) / `<¥50,000` (ja-JP) split rather than converting everything
  to one currency at order time.
- `LegacyCatalogParser` counts and logs what it drops on every seed run
  (`itemsDroppedNoProduct` — an item whose `productId` matches no parsed
  product; `itemLocalesDropped` — a surviving item missing one locale's
  `<ItemDetails>`) so the loss is visible, not silent.
- Using fixed `en_US`/`ja_JP`/`zh_CN` fields instead of a locale-keyed map
  means adding a fourth locale is a schema change, not just new data — an
  acceptable trade for being able to put `@TextIndexed`-equivalent field paths
  on a static text index definition.
