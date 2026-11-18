# Seed data

Static datasets (JSON/CSV) consumed by seed modules live here. Modules should read them
at run time and stay idempotent (upsert on natural keys). Random data must come from
`ctx.faker`, which is seeded with 20261117 so runs are reproducible.
