# ADR: Look barcodes up in Open Food Facts, through an Edge Function

**Date:** 2026-09-05
**Status:** Accepted

## Context
Annona's scanner only ever answered one question: *do we already have this
product?* If not, you got an empty form and typed the name, brand and quantity
by hand — for every new product, standing in a kitchen. The scanner was a
lookup, not data entry.

Open Food Facts is a public, openly licensed database of packaged food, strong
on European products, and it answers most EANs with a name, brand, quantity,
image and the nutrition declaration.

## Decision
A `lookup-food-product` Edge Function queries Open Food Facts and returns the
handful of fields Annona stores. Annona calls it **only when
`fetchProductByEan` finds nothing locally**, so an existing product — which may
have been corrected by hand — is never overwritten by the database's idea of
it.

An Edge Function rather than a direct browser call because the request needs a
`User-Agent` identifying the application (Open Food Facts asks for one and
rate-limits anonymous traffic), the response is large and gets trimmed to what
is kept, and the browser is spared a cross-origin request to a third party. It
verifies the caller's JWT like `fetch-nearby-fuel-prices`, and writes nothing:
the client decides whether to create a product.

Nutrition is stored as discrete columns — the EU declaration per 100 g/ml —
rather than JSON, because the set is fixed, printed on every packet, and worth
having typed. `nutrition_source` records where the values came from, so a
hand-typed product is never mistaken for a looked-up one.

## Consequences
- Scanning an unknown barcode now fills in name, brand, quantity, image and
  nutrition. The form says where the values came from and asks you to check
  them, because Open Food Facts is crowd-sourced and sometimes wrong.
- **Caching is the product row itself.** The second scan of the same barcode
  never reaches Open Food Facts; it finds the local product. There is no other
  cache to invalidate.
- A failed or slow lookup is not an error: it leaves the form empty to type
  into. The function times out at 8 seconds.
- Open Food Facts data is ODbL. The attribution shown on the product detail
  page is a licence condition, not decoration.
- German product names are preferred (`product_name_de`) — the shelf this is
  used in front of is a German one.
- Nutrition for products added before this existed stays empty. Backfilling
  would mean a lookup per product; nothing does that today.
