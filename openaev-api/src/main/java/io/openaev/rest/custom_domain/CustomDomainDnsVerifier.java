package io.openaev.rest.custom_domain;

import java.util.Hashtable;
import javax.naming.NamingEnumeration;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Resolves DNS TXT records to prove a customer owns a custom domain, using the JDK's built-in JNDI
 * DNS provider (no extra dependency). Ownership is asserted by publishing a random challenge token
 * as a TXT record at {@link #CHALLENGE_PREFIX}{@code .<hostname>}; verification passes when any TXT
 * value at that name equals the expected token.
 *
 * <p>Using TXT (not just a CNAME/A pointing at us) is the security gate: a CNAME only proves the
 * record points here, whereas the secret TXT proves the requester controls the zone, so one tenant
 * cannot claim another customer's hostname.
 */
@Slf4j
@Component
public class CustomDomainDnsVerifier {

  /** Sub-domain label under which the ownership TXT challenge is published. */
  public static final String CHALLENGE_PREFIX = "_openaev-challenge";

  /** Outcome of a verification attempt: whether it matched, plus a human-readable detail. */
  public record VerificationResult(boolean verified, String detail) {}

  /**
   * Looks up the challenge TXT record for {@code hostname} and checks it carries {@code
   * expectedToken}. Never throws: DNS/network failures are returned as an un-verified result with a
   * message so the caller can surface it to the admin.
   */
  public VerificationResult verifyTxtChallenge(final String hostname, final String expectedToken) {
    String challengeName = CHALLENGE_PREFIX + "." + normalize(hostname);
    Hashtable<String, String> env = new Hashtable<>();
    env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
    // Fail fast instead of hanging on an unresolvable / unreachable resolver.
    env.put("com.sun.jndi.dns.timeout.initial", "3000");
    env.put("com.sun.jndi.dns.timeout.retries", "2");
    DirContext ctx = null;
    try {
      ctx = new InitialDirContext(env);
      Attributes attributes = ctx.getAttributes(challengeName, new String[] {"TXT"});
      Attribute txt = attributes.get("TXT");
      if (txt == null || txt.size() == 0) {
        return new VerificationResult(
            false, "No TXT record found at " + challengeName + ". Add it and retry.");
      }
      NamingEnumeration<?> values = txt.getAll();
      while (values.hasMore()) {
        String value = stripQuotes(String.valueOf(values.next()));
        if (expectedToken.equals(value)) {
          return new VerificationResult(true, "Ownership confirmed via TXT record.");
        }
      }
      return new VerificationResult(
          false, "TXT record at " + challengeName + " does not match the expected value yet.");
    } catch (javax.naming.NameNotFoundException e) {
      return new VerificationResult(
          false, "No TXT record found at " + challengeName + ". Add it and retry.");
    } catch (Exception e) {
      log.warn("Custom domain TXT verification failed for {}: {}", challengeName, e.getMessage());
      return new VerificationResult(false, "DNS lookup failed: " + e.getMessage());
    } finally {
      if (ctx != null) {
        try {
          ctx.close();
        } catch (Exception ignored) {
          // best-effort close
        }
      }
    }
  }

  /**
   * A single TXT record value may be returned split into quoted chunks (e.g. {@code "abc" "def"});
   * JNDI joins them but keeps the surrounding quotes, which must be removed before comparison.
   */
  private String stripQuotes(final String value) {
    String trimmed = value.trim();
    if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
      trimmed = trimmed.substring(1, trimmed.length() - 1);
    }
    return trimmed.replace("\" \"", "").trim();
  }

  private String normalize(final String hostname) {
    String trimmed = hostname.trim().toLowerCase(java.util.Locale.ROOT);
    return trimmed.endsWith(".") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
  }
}
