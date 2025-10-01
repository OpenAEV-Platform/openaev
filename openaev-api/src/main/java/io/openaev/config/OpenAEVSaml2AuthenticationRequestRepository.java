package io.openaev.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.springframework.security.saml2.provider.service.authentication.AbstractSaml2AuthenticationRequest;
import org.springframework.security.saml2.provider.service.web.HttpSessionSaml2AuthenticationRequestRepository;
import org.springframework.security.saml2.provider.service.web.Saml2AuthenticationRequestRepository;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;

/**
 * This class is heavily based on <a
 * href="https://github.com/spring-projects/spring-security/blob/main/saml2/saml2-service-provider/src/main/java/org/springframework/security/saml2/provider/service/web/HttpSessionSaml2AuthenticationRequestRepository.java">HttpSessionSaml2AuthenticationRequestRepository.java</a>
 * The only difference is in saveAuthenticationRequest where we recreate an HttpSession and copy the
 * attributes from the previous one. This is to prevent an issue where the session would be
 * recreated during the callback after the authentication. To fix this issue, we recreate a new
 * session during the save of the request and copy the attributes to the new session. FIXME : Check
 * if we can remove this custom class when we switch to a more modern saml library
 */
@Component
@Repository
public class OpenAEVSaml2AuthenticationRequestRepository
    implements Saml2AuthenticationRequestRepository<AbstractSaml2AuthenticationRequest> {

  private static final String DEFAULT_SAML2_AUTHN_REQUEST_ATTR_NAME =
      HttpSessionSaml2AuthenticationRequestRepository.class
          .getName()
          .concat(".SAML2_AUTHN_REQUEST");

  private final String saml2AuthnRequestAttributeName = DEFAULT_SAML2_AUTHN_REQUEST_ATTR_NAME;

  @Override
  public AbstractSaml2AuthenticationRequest loadAuthenticationRequest(HttpServletRequest request) {
    HttpSession httpSession = request.getSession(false);
    if (httpSession == null) {
      return null;
    }
    return (AbstractSaml2AuthenticationRequest)
        httpSession.getAttribute(this.saml2AuthnRequestAttributeName);
  }

  @Override
  public void saveAuthenticationRequest(
      AbstractSaml2AuthenticationRequest authenticationRequest,
      HttpServletRequest request,
      HttpServletResponse response) {
    if (authenticationRequest == null) {
      removeAuthenticationRequest(request, response);
      return;
    }
    // Get the session
    HttpSession httpSession = request.getSession();
    Map<String, Object> attributes = new HashMap<>();
    Iterator<String> attributeIterator = httpSession.getAttributeNames().asIterator();
    // Copy the attributes to a new list
    while (attributeIterator.hasNext()) {
      String param = attributeIterator.next();
      attributes.put(param, httpSession.getAttribute(param));
    }
    // Invalidate the previous session to have a fresh one for the login
    httpSession.invalidate();
    HttpSession newSession = request.getSession(true);
    // Set the former attributes into the new session
    attributes.forEach(newSession::setAttribute);
    // Save the request into the new session for ulterior validation
    newSession.setAttribute(this.saml2AuthnRequestAttributeName, authenticationRequest);
  }

  @Override
  public AbstractSaml2AuthenticationRequest removeAuthenticationRequest(
      HttpServletRequest request, HttpServletResponse response) {
    AbstractSaml2AuthenticationRequest authenticationRequest = loadAuthenticationRequest(request);
    if (authenticationRequest == null) {
      return null;
    }
    HttpSession httpSession = request.getSession();
    httpSession.removeAttribute(this.saml2AuthnRequestAttributeName);
    return authenticationRequest;
  }
}
