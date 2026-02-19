package io.openaev.security;

import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.Ed25519Signer;
import com.nimbusds.jose.crypto.Ed25519Verifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jwt.SignedJWT;
import io.openaev.opencti.connectors.ConnectorBase;
import io.openaev.opencti.connectors.impl.SecurityCoverageConnector;
import io.openaev.opencti.connectors.service.OpenCTIConnectorService;
import io.openaev.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

public class OpenCTIJwtAuthenticationFilter extends OncePerRequestFilter {
    private UserService userService;
    private OpenCTIConnectorService openCTIConnectorService;

    @Autowired
    public void setUserService(UserService userService) {
        this.userService = userService;
    }
    @Autowired
    public void setOpenCTIConnectorService(OpenCTIConnectorService openCTIConnectorService) {
        this.openCTIConnectorService = openCTIConnectorService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // only runs for /api/stix/** — skipped for everything else
        return !request.getRequestURI().startsWith("/api/stix/");
    }


//    public void decodeAndVerify(String token) throws Exception {
//        Optional<ConnectorBase> connector = openCTIConnectorService.getConnectorBase();
//        if (connector.isEmpty()) {
//            throw new ServletException("Connector not found");
//        }
//
//        SecurityCoverageConnector openCTIConnector = (SecurityCoverageConnector) connector.get();
//        JWKSet jwkSet = JWKSet.parse(openCTIConnector.getJwks());
//        OctetKeyPair okpKey = (OctetKeyPair) jwkSet.getKeys().get(0);
//
//        SignedJWT signedJWT = SignedJWT.parse(token);
//
//        Ed25519Verifier verifier = new Ed25519Verifier(okpKey.toPublicJWK());
//        if (!signedJWT.verify(verifier)) {
//            throw new Exception("Failed");
//        }
//
//        JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
//
//        // Check expiry manually
//        if (claims.getExpirationTime() != null &&
//                claims.getExpirationTime().toInstant().isBefore(Instant.now())) {
////                throw new JwtException("")
//            throw new Exception("JWT token has expired");
//        }
//
//    }

    public void decodeAndVerify(String token) throws Exception {
        Optional<ConnectorBase> openCTIConnector = openCTIConnectorService.getConnectorBase();
        System.out.println(openCTIConnector.isPresent());
        if (openCTIConnector.isEmpty()) {
            throw new ServletException("Connector not found");
        }
        String jwksJson = ((SecurityCoverageConnector) openCTIConnector.get()).getJwks();
        JWKSet jwkSet = JWKSet.parse(jwksJson);
        OctetKeyPair okpKey = (OctetKeyPair) jwkSet.getKeys().get(0);

        SignedJWT signedJWT = SignedJWT.parse(token);
        Ed25519Verifier verifier = new Ed25519Verifier(okpKey.toPublicJWK());


        boolean verified = signedJWT.verify(verifier);
        System.out.println("=== Manual verify result: " + verified); // must be true
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring("Bearer ".length()).trim();

        try {
            decodeAndVerify(token);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        this.userService.createAdminSession();
        filterChain.doFilter(request, response);
    }

}
