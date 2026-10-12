# Cyoria Identity prototype

See [IDENTITY-DESIGN.md](IDENTITY-DESIGN.md) for the user-visible rules, implementation status, state transitions and mandatory security gates. This prototype deliberately changes no live login behavior.

Run the phase-1 tests:

```sh
./gradlew :core:test --tests '*CyoriaIdentityRegistryTest'
```

License: upstream FastLoginPlus and this fork remain under MIT, with original notices retained. All features are experimental until integration tests run against an isolated test server.
