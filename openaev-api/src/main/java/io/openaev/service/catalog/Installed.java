package io.openaev.service.catalog;

import java.util.List;

/** What an install of several versions did: what it installed, and the versions that failed. */
public record Installed<V extends InstallableVersion, R>(List<R> installed, List<V> failed) {}
