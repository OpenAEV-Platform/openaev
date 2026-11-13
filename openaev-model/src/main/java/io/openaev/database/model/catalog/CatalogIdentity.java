package io.openaev.database.model.catalog;

import io.openaev.database.model.TenantBase;

/** What a tenant knows of a catalogue item, installed or not: its repository and external ID. */
public interface CatalogIdentity extends TenantBase {

  String getRepository();

  String getExternalId();
}
