package io.openaev.secrets.provider;

import io.openaev.database.model.HashSecret;

/**
 * Safe metadata returned by secret providers for credential display/update helpers.
 *
 * <p>It intentionally excludes any secret value (password/hash), even encrypted.
 */
public record SecretMetadata(String username, HashSecret.HASH_ALGORITHM hashAlgorithm) {}
