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
`V6_{nanosecond_timestamp}__name_of_the_migration`

where `nanosecond_timestamp` is of the form `yyyyMMddhhmmssSSS`.

Example contents of the `migration` directory:
```shell
...
├── V5_24__Add_Indexing_Cursor_Indexes.java
├── V5_25__Add_exercise_coverage_index.java
└── V6_20260629104352746__add_columns_to_some_table.java
```
On the next app boot, the new migration will be picked up and run against the existing schema on the database backend.

### Creating a new migration file
A migration file may be created manually as long as the class name (including the version code) is unique and fulfils
the requirements for a Flyway schema migration.

OpenAEV includes a maven plugin for helping create these migration files, especially with the correct timestamp: [OpenAEV Maven Plugin](https://github.com/OpenAEV-Platform/openaev/blob/2597eae21b7886da93b9acdda4583aa8a2d63233/openaev-maven-plugin/README.md)

It must be used from within the `openaev-api` java module:
```shell
(openaev-api) $ mvn openaev:migration
```
Optionally, give a "reason" argument to customise the class name and give it a human readable purpose:
```shell
(openaev-api) $ mvn openaev:migration -Dreason="add columns to some table"
```
The new migration class file and skeleton contents are now created in the `migration` directory. 

## Out-of-order migration processing
By default, Flyway imposes a strictly sequential order for running migrations, which means that
if, between two app boots, one or more new migrations classes are detected and their class names are not all ranked at the 
end of the lexicographical order, Flyway will output an error and exit, preventing schema changes.

OpenAEV exposes an environment variable to enable the "out-of-order" mode in Flyway, which allows migrations to
run in any order, as they are detected from boot to boot.
```shell
OPENAEV_FLYWAY_OUT_OF_ORDER=true|false
```
* `true`: enable the out-of-order mode
* `false` (default): enforce strictly sequential processing of migrations

Alternatively, set the following Spring property: `spring.flyway.out-of-order` (`true|false`).

Note that at any one time, all unprocessed migrations are still run sequentially in lexicographical order.