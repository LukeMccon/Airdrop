# Package Price Validation Design

## Goal

Reject configured packages whose raw `price` value is missing, non-numeric, non-finite after conversion to `double`, or negative. Valid numeric zero values remain accepted.

## Approach

Validation belongs in `PackageManager.populatePackages()`, the boundary that converts YAML configuration into runtime packages. The loader will read the raw value with `ConfigurationSection.get`, require a `Number`, convert it with `doubleValue()`, and reuse `Package.isValidPrice` for the finite, non-negative range check. Any failure logs the package name and a printable representation of the raw value, then skips registration.

`PackageManager.getPackages()` will return a snapshot of the in-memory registry keys rather than raw YAML keys. Lookup already reads the same registry, while command completion and `PackagesGui` enumerate through `getPackages()`. This makes every package-facing surface observe the same validated set.

## Error Handling

Invalid values do not abort the full reload and do not fall back to a free price. Each invalid package produces one warning of the form `Skipping package '<name>' because its price is invalid: <value>`; a missing value is rendered as `<missing>`. Other valid packages continue loading.

## Tests

Focused configuration tests will cover missing price, numeric string, Boolean, NaN, infinity, negative value, a large `BigDecimal` that converts to infinity, integer zero, and floating-point zero. Tests will assert registry membership, lookup behavior, and warning content. Command-completion fixtures will reload the registry and directly assert that a rejected entry is absent from both completion paths. A package-browser test will directly assert that an invalid configured entry does not create an inventory item. Existing tests plus the full Gradle build will guard packaging.
