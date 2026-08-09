package io.openaev.rest.custom_domain;

import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;

import io.openaev.config.OpenAEVConfig;
import io.openaev.database.model.CustomDomain;
import io.openaev.database.model.CustomDomain.CustomDomainStatus;
import io.openaev.database.repository.CustomDomainRepository;
import io.openaev.rest.custom_domain.response.CustomDomainInstructions;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD and DNS ownership verification for {@link CustomDomain}s. A domain is created {@code
 * PENDING} with a random TXT challenge token; the customer publishes the {@link
 * CustomDomainDnsVerifier#CHALLENGE_PREFIX} TXT record and triggers verification, which flips the
 * domain to {@code VERIFIED} - the only state in which it may be linked to a landing page or
 * answered by the public {@code domain-check} endpoint.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CustomDomainService {

  // RFC 1123 hostname: labels of a-z/0-9/-, each 1-63 chars, at least two labels (a real FQDN).
  private static final Pattern HOSTNAME_PATTERN =
      Pattern.compile(
          "^(?=.{1,253}$)([a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)(\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+$");

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final CustomDomainRepository customDomainRepository;
  private final CustomDomainDnsVerifier dnsVerifier;
  private final OpenAEVConfig openAEVConfig;

  public Page<CustomDomain> search(@NotNull final SearchPaginationInput input) {
    return buildPaginationJPA(
        (Specification<CustomDomain> specification, Pageable pageable) ->
            customDomainRepository.findAll(specification, pageable),
        input,
        CustomDomain.class);
  }

  public CustomDomain customDomain(@NotBlank final String id) {
    return customDomainRepository.findById(id).orElseThrow(ElementNotFoundException::new);
  }

  public CustomDomain create(@NotBlank final String rawHostname) {
    String hostname = normalizeHostname(rawHostname);
    customDomainRepository
        .findByHostnameIgnoreCase(hostname)
        .ifPresent(
            existing -> {
              throw new BadRequestException("This domain is already registered");
            });
    CustomDomain domain = new CustomDomain();
    domain.setHostname(hostname);
    domain.setStatus(CustomDomainStatus.PENDING);
    domain.setVerificationToken(generateToken());
    domain.setUpdatedAt(Instant.now());
    try {
      return customDomainRepository.save(domain);
    } catch (org.springframework.dao.DataIntegrityViolationException e) {
      // Global unique index: the hostname is already claimed by another tenant.
      throw new BadRequestException("This domain is already registered");
    }
  }

  public void delete(@NotBlank final String id) {
    CustomDomain domain = customDomain(id);
    customDomainRepository.deleteById(domain.getId());
  }

  /** Runs the DNS TXT ownership check and persists the resulting status. */
  public CustomDomain verify(@NotBlank final String id) {
    CustomDomain domain = customDomain(id);
    CustomDomainDnsVerifier.VerificationResult result =
        dnsVerifier.verifyTxtChallenge(domain.getHostname(), domain.getVerificationToken());
    Instant now = Instant.now();
    domain.setLastCheckedAt(now);
    domain.setUpdatedAt(now);
    if (result.verified()) {
      domain.setStatus(CustomDomainStatus.VERIFIED);
      domain.setVerifiedAt(now);
      domain.setLastError(null);
    } else {
      // Keep an already-verified domain verified; only a never-verified domain drops to FAILED.
      if (domain.getStatus() != CustomDomainStatus.VERIFIED) {
        domain.setStatus(CustomDomainStatus.FAILED);
      }
      domain.setLastError(result.detail());
    }
    return customDomainRepository.save(domain);
  }

  public CustomDomainInstructions instructions(@NotBlank final String id) {
    CustomDomain domain = customDomain(id);
    return new CustomDomainInstructions(
        domain.getHostname(),
        domain.getHostname(),
        platformHost(),
        CustomDomainDnsVerifier.CHALLENGE_PREFIX + "." + domain.getHostname(),
        domain.getVerificationToken());
  }

  /**
   * Whether a hostname resolves to a VERIFIED custom domain. Tenant-context-free (native query),
   * used by the public {@code domain-check} endpoint that fronts on-demand TLS at the edge.
   */
  public boolean isHostnameVerified(final String hostname) {
    if (hostname == null || hostname.isBlank()) {
      return false;
    }
    return customDomainRepository
        .findStatusByHostname(normalizeHostname(hostname))
        .map(CustomDomainStatus.VERIFIED.name()::equals)
        .orElse(false);
  }

  private String platformHost() {
    String baseUrl = openAEVConfig.getBaseUrl();
    if (baseUrl == null || baseUrl.isBlank()) {
      return "";
    }
    try {
      String host = URI.create(baseUrl).getHost();
      return host != null ? host : baseUrl;
    } catch (IllegalArgumentException e) {
      return baseUrl;
    }
  }

  private String normalizeHostname(final String rawHostname) {
    if (rawHostname == null) {
      throw new BadRequestException("Hostname is required");
    }
    String hostname = rawHostname.trim().toLowerCase(Locale.ROOT);
    if (hostname.endsWith(".")) {
      hostname = hostname.substring(0, hostname.length() - 1);
    }
    // Reject anything that is not a bare hostname (scheme, path, port, wildcard, userinfo).
    if (!HOSTNAME_PATTERN.matcher(hostname).matches()) {
      throw new BadRequestException(
          "Enter a valid fully-qualified domain name (e.g. security.acme.com)");
    }
    return hostname;
  }

  private String generateToken() {
    byte[] buffer = new byte[24];
    SECURE_RANDOM.nextBytes(buffer);
    return "openaev-domain-verification="
        + Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
  }
}
