package io.openaev.service.catalog;

/** A released catalogue version to install. */
public interface InstallableVersion {

  String externalId();

  String version();
}
