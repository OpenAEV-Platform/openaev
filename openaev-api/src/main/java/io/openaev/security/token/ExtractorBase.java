package io.openaev.security.token;

import io.jsonwebtoken.JwtException;
import io.openaev.opencti.errors.ConnectorError;
import io.openaev.security.error.AuthenticationError;

public interface ExtractorBase {
  String extractToken(String value) throws ConnectorError, JwtException, AuthenticationError;
}
