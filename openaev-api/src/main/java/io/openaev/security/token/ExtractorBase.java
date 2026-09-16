package io.openaev.security.token;

import io.jsonwebtoken.JwtException;
import io.openaev.database.model.User;
import io.openaev.opencti.errors.ConnectorError;
import io.openaev.security.error.AuthenticationError;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;

public interface ExtractorBase {
  Optional<User> authUser(String value, HttpServletRequest request)
      throws ConnectorError, JwtException, AuthenticationError;
}
