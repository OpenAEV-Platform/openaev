# Database migrations

OpenAEV uses [Flyway](https://www.red-gate.com/products/flyway/) as its database schema management tool,
including for tracking schema changes (migrations).

## First time schema creation
On the first boot of the OpenAEV platform application, the schema will be created by running all the available
migrations from the `openaev-api/src/main/java/io/openaev/migration` directory, in lexicographical order of their
respective class names.

## Schema updates
To update the schema, create a new migration class ranked at the end of the lexicographical order, using the following
pattern:
`V6_{timestamp}__name_of_the_migration`

where `timestamp` is the current UTC time in the form `yyyyMMddHHmmssSSS`.

Example contents of the `migration` directory:
```text
...
├── V5_24__Add_Indexing_Cursor_Indexes.java
├── V5_25__Add_exercise_coverage_index.java
└── V6_20260629104352746__add_columns_to_some_table.java
```
On the next app boot, the new migration will be picked up and run against the existing schema on the database backend.

### Creating a new migration file
A migration file may be created manually as long as the class name (including the version code) is unique and fulfils
the requirements for a Flyway schema migration.

OpenAEV includes a Maven plugin that creates these migration files with the correct timestamp: [OpenAEV Maven Plugin](https://github.com/OpenAEV-Platform/openaev/blob/main/openaev-maven-plugin/README.md).
Install it first: `mvn clean install -DskipTests` from the repository root does it (see [Build from source](build-from-source.md#building-and-running)).

Run it from the `openaev-api` module:
```bash
cd openaev-api
mvn openaev:migration
```
Optionally, give a "reason" argument to set a readable class name:
```bash
mvn openaev:migration -Dreason="add columns to some table"
```
The new migration class file and skeleton contents are now created in the `migration` directory.

!!! warning

    Never edit, rename or delete a migration that is part of a release. The CI migrations guard compares file
    names with the last release: it fails on a renamed or deleted migration, or a new one that does not sort
    last. It does not detect edits inside a released migration.

## Out-of-order migration processing
By default, Flyway imposes a strictly sequential order for running migrations, which means that
if, between two app boots, one or more new migrations classes are detected and their class names are not all ranked at the 
end of the lexicographical order, Flyway will output an error and exit, preventing schema changes.

OpenAEV exposes an environment variable to enable the "out-of-order" mode in Flyway, which allows migrations to
run in any order, as they are detected from boot to boot.
```bash
OPENAEV_FLYWAY_OUT_OF_ORDER=true|false
```
* `true`: enable the out-of-order mode
* `false` (default): enforce strictly sequential processing of migrations

Alternatively, set the following Spring property: `spring.flyway.out-of-order` (`true|false`).

Note that at any one time, all unprocessed migrations are still run sequentially in lexicographical order.

## What's next?

- [Build from source](build-from-source.md) -- Build and run OpenAEV locally
- [Platform development](platform.md) -- Day-to-day backend and frontend workflow
- [Breaking changes](../deployment/breaking-changes.md) -- Changes that need action when upgrading
