# openaev-maven-plugin

Plugin for managing common tasks for the development of OpenAEV

## Installation (mandatory)
Within the maven plugin root directory, run the install command.
This installs the plugin in the local maven repository.
```shell
mvn clean install
```

## Usage

### Create a new empty Flyway migration file
**Note: this is only intended for being run within the OpenAEV API module.**

This will create an empty migration file titled `V6_{timestamp}__migration`.
```shell
mvn openaev:migration
```
```shell
V6_20260619103476928__migration.java
```

It is possible to specify a "reason" for giving a more explicit name to the migration.
```shell
mvn openaev:migration -Dreason="add more columns to table"
```
```shell
V6_20260619103476928__add_more_columns_to_table.java
```