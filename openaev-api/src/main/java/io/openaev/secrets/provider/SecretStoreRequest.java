package io.openaev.secrets.provider;

/**
 * Payload for provider-side secret storage.
 *
 * @param username optional username for USERNAME_PASSWORD auth method
 * @param password plaintext password for USERNAME_PASSWORD auth method
 * @param hash plaintext hash for HASH auth method
 * @param hashAlgorithm hash algorithm for HASH auth method (NTLM, SHA256, ...)
 */
public record SecretStoreRequest(
    String username, String password, String hash, String hashAlgorithm) {}
