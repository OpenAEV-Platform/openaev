package io.openaev.security.token;

import io.jsonwebtoken.JwtException;
import io.openaev.opencti.errors.ConnectorError;

public interface ExtractorBase {
  String extractToken(String value) throws ConnectorError, JwtException;
}
