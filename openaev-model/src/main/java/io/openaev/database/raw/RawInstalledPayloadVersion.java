package io.openaev.database.raw;

/** The version a tenant shows of one installed catalogue payload. */
public interface RawInstalledPayloadVersion {

  String getRepository();

  String getExternalId();

  String getVersion();
}
