package io.openaev.debug;

import java.time.Duration;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

/**
 * Configuration for the global debug mode. Driven by the single {@code openaev.debug.enabled} flag,
 * off by default (no beans, no proxy, no per-request cost when off). Activation is instance-wide.
 */
@Component
@Data
@ConfigurationProperties(prefix = "openaev.debug")
public class DebugProperties {

  /** Master switch. Off by default. */
  private boolean enabled = false;

  /**
   * Override required to start in production (no dev/test/ci profile); separate from {@code
   * enabled}.
   */
  private boolean allowInProduction = false;

  /** Auto-disable the verbose tracing after this duration. {@code 0} = never. */
  private Duration autoDisableAfter = Duration.ZERO;

  /** How often the "debug mode is active" warning repeats. */
  private Duration warningInterval = Duration.ofMinutes(5);

  /**
   * Writable directory for the JFR recordings and the rotated debug log files (SQL, and the ORM
   * summary when {@code orm.summary-to-file} is on).
   */
  private String outputDir = "./logs/debug";

  private final Sql sql = new Sql();
  private final Orm orm = new Orm();
  private final Jfr jfr = new Jfr();
  private final Masking masking = new Masking();

  /** SQL statement logging (timing + masked parameters), via datasource-proxy. */
  @Data
  public static class Sql {
    /** Log SQL statements. */
    private boolean enabled = true;

    /** Only log statements slower than this. {@code 0} logs all. */
    private Duration slowQueryThreshold = Duration.ZERO;

    /** Truncate rendered parameter values longer than this. */
    private int maxParameterLength = 200;

    /** Rotated SQL file: size a single file reaches before it rolls over. */
    private DataSize maxFileSize = DataSize.ofMegabytes(500);

    /** Rotated SQL file: number of days of history to keep. */
    private int maxHistory = 7;

    /**
     * Rotated SQL file: total size kept across all files before the oldest are deleted. Raise this
     * on high-traffic instances where the log fills fast, to keep more history.
     */
    private DataSize totalSizeCap = DataSize.ofGigabytes(2);
  }

  /** Per-request ORM/N+1 summary (one log event per request on {@code io.openaev.debug.orm}). */
  @Data
  public static class Orm {
    /**
     * Write the ORM summary to a rotated file instead of the console. Useful on instances whose
     * console is shipped to centralised logging, to keep the per-request summaries out of it.
     */
    private boolean summaryToFile = false;
  }

  /** Java Flight Recorder capture via the JDK {@code jdk.jfr} engine. */
  @Data
  public static class Jfr {
    /** Start a JFR recording. */
    private boolean enabled = true;

    /** Cap on a single recording's on-disk size. */
    private DataSize maxSize = DataSize.ofMegabytes(100);

    /** Max age of events kept in the buffer. */
    private Duration maxAge = Duration.ofHours(1);

    /** Interval between periodic dumps. */
    private Duration duration = Duration.ofMinutes(10);

    /**
     * Built-in profile: {@code profile} (richer, non-trivial overhead) or {@code default} (light).
     */
    private String settings = "profile";

    /** Retention: delete oldest dumps past this count. */
    private int maxDumpFiles = 12;

    /** Retention: delete oldest dumps past this total size. */
    private DataSize maxTotalDumpSize = DataSize.ofMegabytes(500);
  }

  /** Masking of secrets and personal data. */
  @Data
  public static class Masking {
    /** Master switch for masking. */
    private boolean enabled = true;

    /** Deny-by-default: mask every value (keep column + type) and statement-text literals. */
    private boolean maskAllParameters = false;

    /** Replacement token. */
    private String mask = "***MASKED***";

    /** Field/column names (case-insensitive substring) whose value is always masked. */
    private List<String> sensitiveKeys =
        List.of(
            "password",
            "passwd",
            "pass",
            "secret",
            "token",
            "api_key",
            "apikey",
            "api-key",
            "access_key",
            "access_secret",
            "encryption_key",
            "encryption_salt",
            "private_key",
            "privatekey",
            "credential",
            "credentials",
            "authorization",
            "cookie",
            "client_secret",
            "trust-store-password",
            "ssn");

    /** Regexes masked anywhere they match (secrets/PII with no key context). */
    private List<String> valuePatterns =
        List.of(
            // JSON Web Token
            "eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+",
            // Bearer / Basic authorization header value
            "(?i)(bearer|basic)\\s+[A-Za-z0-9._\\-+/=]+",
            // PEM private key block
            "-----BEGIN[^-]*PRIVATE KEY-----",
            // Email address (personal data)
            "[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}");
  }
}
