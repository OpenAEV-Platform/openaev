package io.openaev.database.raw;

/** The version a tenant installed of one catalogue item. */
public interface RawInstalledCatalogVersion {

  String getRepository();

  String getExternalId();

  String getVersion();
}
