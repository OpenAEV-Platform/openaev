package io.openaev.service.autonomous;

import io.openaev.database.model.autonomous.AutonomousObjectiveTemplate;
import io.openaev.database.repository.autonomous.AutonomousObjectiveTemplateRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the objective-template gallery for autonomous runs. Built-ins are seeded lazily for the
 * calling tenant on first read (idempotent by key), so every tenant gets the catalog without a
 * cross-tenant seed migration, and admins can still add or disable their own.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AutonomousObjectiveTemplateService {

  private final AutonomousObjectiveTemplateRepository repository;

  /**
   * The built-in objective catalog. Free-text objectives are always allowed on top of these; the
   * gallery is a rich starting point, not an exhaustive grammar.
   */
  private record Builtin(
      String key,
      String label,
      String description,
      String icon,
      String killChainFocus,
      String scopeMode,
      String prompt) {}

  /** Objective operates over the whole authorized environment; no target choice needed. */
  private static final String SCOPE_ENVIRONMENT = "environment";

  /** Objective is meaningless without a specific target the operator picks (or the AI asks for). */
  private static final String SCOPE_TARGET = "target";

  private static final List<Builtin> BUILTINS =
      List.of(
          new Builtin(
              "reach-domain-controller",
              "Reach the Domain Controller",
              "Discover a path from the initial foothold to a domain controller and prove"
                  + " privileged access.",
              "domain",
              "lateral-movement",
              SCOPE_ENVIRONMENT,
              "Starting from the in-scope perimeter, perform reconnaissance, find and exploit a"
                  + " foothold, escalate privileges, and move laterally until you reach and prove"
                  + " administrative access on a domain controller. Prefer the shortest credible"
                  + " path and record every hop as proof."),
          new Builtin(
              "prove-data-exfiltration",
              "Prove Data Exfiltration",
              "Reach sensitive data stores and demonstrate a non-destructive exfiltration path.",
              "database",
              "exfiltration",
              SCOPE_ENVIRONMENT,
              "Identify sensitive data repositories inside the scope, obtain access to them, and"
                  + " demonstrate a viable exfiltration channel with a benign marker file. Do not"
                  + " destroy or alter real data; the goal is to prove the path end to end."),
          new Builtin(
              "validate-edr-detections",
              "Validate EDR / SIEM Detections",
              "Exercise a broad set of techniques to measure which are detected and prevented.",
              "shield",
              null,
              SCOPE_ENVIRONMENT,
              "Run a representative spread of ATT&CK techniques across the kill chain against the"
                  + " in-scope assets and report, per technique, whether it was prevented,"
                  + " detected, or missed by the security stack. Optimise for coverage of the"
                  + " defensive gaps rather than for a single deep path."),
          new Builtin(
              "initial-access-foothold",
              "Gain Initial Access",
              "Find and exploit an exposed service or credential to establish a first foothold.",
              "door-open",
              "initial-access",
              SCOPE_ENVIRONMENT,
              "Enumerate the externally or internally exposed surface of the scope, identify the"
                  + " weakest exploitable entry point (vulnerable service, default or leaked"
                  + " credential, misconfiguration) and establish an initial foothold. Stop once a"
                  + " foothold is proven and report the entry technique."),
          new Builtin(
              "privilege-escalation",
              "Escalate Privileges",
              "From an existing foothold, escalate to local or domain administrator.",
              "arrow-up",
              "privilege-escalation",
              SCOPE_ENVIRONMENT,
              "Assume a low-privilege foothold on an in-scope host and escalate to the highest"
                  + " privilege reachable on that host and in the domain. Chain misconfigurations,"
                  + " vulnerable services, and harvested credentials as needed."),
          new Builtin(
              "harvest-credentials",
              "Harvest Credentials",
              "Collect valid credentials via phishing, dumping, or scanning to enable movement.",
              "key",
              "credential-access",
              SCOPE_ENVIRONMENT,
              "Obtain valid credentials for in-scope identities using the available capabilities"
                  + " (credential dumping, phishing, brute force against exposed services, secret"
                  + " scanning). Report each credential with how it was obtained and what it"
                  + " unlocks."),
          new Builtin(
              "phishing-to-access",
              "Phishing to Access",
              "Run a credential-harvesting phishing campaign and pivot the captured access.",
              "mail",
              "initial-access",
              SCOPE_ENVIRONMENT,
              "Design and launch a phishing campaign against the in-scope audience to capture"
                  + " credentials or execution, then use the captured access as a foothold and"
                  + " continue toward broader access. Keep payloads benign and clearly marked."),
          new Builtin(
              "lateral-movement-sweep",
              "Lateral Movement Sweep",
              "From one compromised host, spread as widely as possible across the scope.",
              "network",
              "lateral-movement",
              SCOPE_ENVIRONMENT,
              "Starting from a single compromised host, enumerate reachable systems and move"
                  + " laterally to compromise as many in-scope hosts as possible, reusing"
                  + " harvested credentials and trust relationships. Map the blast radius."),
          new Builtin(
              "ransomware-simulation",
              "Ransomware Kill-Chain Simulation",
              "Emulate a ransomware operator end to end with non-destructive proof actions.",
              "lock",
              "impact",
              SCOPE_ENVIRONMENT,
              "Emulate a full ransomware operator kill chain (initial access, discovery,"
                  + " credential access, lateral movement, staging) and, at the impact stage, use"
                  + " only benign proof actions (drop a marker file, list what would be encrypted)."
                  + " Never actually encrypt, delete, or damage data."),
          new Builtin(
              "crown-jewel-assessment",
              "Crown-Jewel Assessment",
              "Target a specified business-critical asset and prove whether it can be reached.",
              "gem",
              null,
              SCOPE_TARGET,
              "Treat the operator-named asset (see the objective detail) as the crown jewel. Find"
                  + " and prove the most credible attack path to compromise it, or conclude with"
                  + " evidence that it is not reachable from the current scope."),
          new Builtin(
              "web-app-exploitation",
              "Web Application Exploitation",
              "Focus on in-scope web applications and chain web vulns into deeper access.",
              "globe",
              "initial-access",
              SCOPE_TARGET,
              "Concentrate on the in-scope web applications: enumerate endpoints, find and exploit"
                  + " web vulnerabilities (injection, auth bypass, SSRF, file upload) and chain"
                  + " them into server or credential access. Report each exploited vulnerability."),
          new Builtin(
              "assumed-breach",
              "Assumed-Breach Assessment",
              "Start from a given foothold and measure how far an attacker gets from there.",
              "user-check",
              null,
              SCOPE_ENVIRONMENT,
              "Assume the attacker already has the foothold described in the objective detail."
                  + " From there, pursue maximum impact: privilege escalation, lateral movement,"
                  + " and access to sensitive assets, quantifying how far a breach of that entry"
                  + " point would spread."));

  @Transactional
  public List<AutonomousObjectiveTemplate> listForCurrentTenant() {
    ensureBuiltinsSeeded();
    return repository.findByEnabledTrueOrderByOrderAsc();
  }

  @Transactional(readOnly = true)
  public AutonomousObjectiveTemplate findByKeyOrNull(String key) {
    if (key == null || key.isBlank()) {
      return null;
    }
    return repository.findByKey(key).orElse(null);
  }

  /** Idempotently materialises any missing built-in for the calling tenant. */
  private void ensureBuiltinsSeeded() {
    int order = 0;
    for (Builtin b : BUILTINS) {
      order += 10;
      if (repository.existsByKey(b.key())) {
        continue;
      }
      AutonomousObjectiveTemplate template = new AutonomousObjectiveTemplate();
      template.setKey(b.key());
      template.setLabel(b.label());
      template.setDescription(b.description());
      template.setIcon(b.icon());
      template.setPrompt(b.prompt());
      template.setKillChainFocus(b.killChainFocus());
      template.setScopeMode(b.scopeMode());
      template.setBuiltin(true);
      template.setEnabled(true);
      template.setOrder(order);
      try {
        repository.save(template);
      } catch (Exception e) {
        // A concurrent first-read in the same tenant may have seeded it; ignore the unique clash.
        log.debug("[Autonomous] Objective template {} already seeded", b.key());
      }
    }
  }

  @Transactional
  public AutonomousObjectiveTemplate create(AutonomousObjectiveTemplate template) {
    template.setBuiltin(false);
    return repository.save(template);
  }

  @Transactional
  public AutonomousObjectiveTemplate update(AutonomousObjectiveTemplate template) {
    return repository.save(template);
  }

  @Transactional
  public void delete(String id) {
    repository.deleteById(id);
  }
}
