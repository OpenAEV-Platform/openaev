package io.openaev.utils;

import io.openaev.database.model.*;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.hibernate.Hibernate;

/**
 * Utility class for agent-related operations and validations.
 *
 * <p>Provides helper methods for filtering, validating, and retrieving agents associated with
 * assets and injects. Agents are software components deployed on endpoints that execute inject
 * commands during simulations.
 *
 * <p>This is a utility class and cannot be instantiated.
 *
 * @see io.openaev.database.model.Agent
 * @see io.openaev.database.model.Endpoint
 */
public class AgentUtils {

  private AgentUtils() {}

  /**
   * List of supported platform types.
   *
   * <p>Contains: Linux, Windows, macOS
   */
  private static final List<Endpoint.PLATFORM_TYPE> SUPPORTED_AGENT_PLATFORMS =
      List.of(
          Endpoint.PLATFORM_TYPE.Linux,
          Endpoint.PLATFORM_TYPE.Windows,
          Endpoint.PLATFORM_TYPE.MacOS);

  /**
   * resolves a platform enum option from a string, and validates against a subset of supported
   * values
   *
   * @param platformString the input string representing a platform type
   * @return the resolved platform type
   * @throws IllegalArgumentException when the platform is not one that is allowed
   */
  public static Endpoint.PLATFORM_TYPE normaliseSupportedAgentPlatform(String platformString)
      throws IllegalArgumentException {
    Endpoint.PLATFORM_TYPE resolved = Endpoint.PLATFORM_TYPE.fromString(platformString);
    if (!SUPPORTED_AGENT_PLATFORMS.contains(resolved)) {
      throw new IllegalArgumentException("Unsupported platform: " + platformString);
    }
    return resolved;
  }

  /**
   * List of supported CPU architectures.
   *
   * <p>Contains: x86_64, arm64
   */
  private static final List<Endpoint.PLATFORM_ARCH> SUPPORTED_AGENT_ARCHITECTURES =
      List.of(Endpoint.PLATFORM_ARCH.x86_64, Endpoint.PLATFORM_ARCH.arm64);

  /**
   * resolves an arch enum option from a string, and validates against a subset of supported values
   *
   * @param archString the input string representing an arch type
   * @return the resolved arch type
   * @throws IllegalArgumentException when the arch is not one that is allowed
   */
  public static Endpoint.PLATFORM_ARCH normaliseSupportedAgentArch(String archString)
      throws IllegalArgumentException {
    Endpoint.PLATFORM_ARCH resolved =
        Endpoint.PLATFORM_ARCH.fromString(getCanonicalArchitectureString(archString));
    if (!SUPPORTED_AGENT_ARCHITECTURES.contains(resolved)) {
      throw new IllegalArgumentException("Unsupported architecture: " + archString);
    }
    return resolved;
  }

  /** All keys must be lowercase */
  private static final Map<String, String> ARCHITECTURE_ALIAS =
      Map.of("aarch64", Endpoint.PLATFORM_ARCH.arm64.name().toLowerCase());

  /**
   * Will transform a supported architecture name alias into its canonical name
   *
   * @param architecture architecture for which to possibly find a canonical name
   * @return canonical architecture string
   */
  public static String getCanonicalArchitectureString(String architecture) {
    return ARCHITECTURE_ALIAS.getOrDefault(architecture.toLowerCase(), architecture.toLowerCase());
  }

  private enum INSTALLATION_MODE {
    SERVICE("service"),
    SERVICE_USER("service-user"),
    SESSION_USER("session-user");

    private final String value;

    INSTALLATION_MODE(String value) {
      this.value = value;
    }

    public String getValue() {
      return this.value;
    }

    public static INSTALLATION_MODE fromString(String candidate) {
      for (INSTALLATION_MODE mode : INSTALLATION_MODE.values()) {
        if (mode.value.equalsIgnoreCase(candidate)) {
          return mode;
        }
      }
      throw new IllegalArgumentException("Unsupported installation mode");
    }
  }

  /**
   * resolves a normalised installation mode string based on a list of supported installation modes
   *
   * @param installationModeString the desired installation mode
   * @return the normalised installation mode string
   */
  public static String getSupportedInstallationMode(String installationModeString) {
    return INSTALLATION_MODE.fromString(installationModeString).getValue();
  }

  /**
   * Retrieves all active and valid agents from an asset for a specific inject.
   *
   * <p>This method unproxies the asset to access the underlying endpoint and filters its agents
   * based on validity criteria for the given inject.
   *
   * @param asset the asset (endpoint) containing the agents
   * @param inject the inject context used for validation
   * @return a list of active agents that pass all validation checks
   * @see #isValidAgent(Inject, Agent)
   */
  public static List<Agent> getActiveAgents(Asset asset, Inject inject) {
    return ((Endpoint) Hibernate.unproxy(asset))
        .getAgents().stream().filter(agent -> isValidAgent(inject, agent)).toList();
  }

  /**
   * Validates whether an agent is suitable for executing an inject.
   *
   * <p>An agent is considered valid if it meets all of the following criteria:
   *
   * <ul>
   *   <li>It is a primary agent (not a child or inject-specific agent)
   *   <li>It has no error or inactive traces for the given inject
   *   <li>It is currently active
   * </ul>
   *
   * @param inject the inject to validate against
   * @param agent the agent to validate
   * @return {@code true} if the agent is valid for the inject, {@code false} otherwise
   */
  public static boolean isValidAgent(Inject inject, Agent agent) {
    return isPrimaryAgent(agent) && hasOnlyValidTraces(inject, agent) && agent.isActive();
  }

  /**
   * Checks if an agent has only valid execution traces for a specific inject.
   *
   * <p>An agent has valid traces if none of its traces for the inject have an ERROR or
   * AGENT_INACTIVE status. If no traces exist, the agent is considered valid by default.
   *
   * @param inject the inject to check traces for
   * @param agent the agent whose traces are being validated
   * @return {@code true} if no invalid traces exist, {@code false} if error or inactive traces are
   *     found
   */
  public static boolean hasOnlyValidTraces(Inject inject, Agent agent) {
    return inject
        .getStatus()
        .map(InjectStatus::getTraces)
        .map(
            traces ->
                Boolean.valueOf(
                    traces.stream()
                        .noneMatch(
                            trace ->
                                trace.getAgent() != null
                                    && trace.getAgent().getId().equals(agent.getId())
                                    && (ExecutionTraceStatus.ERROR.equals(trace.getStatus())
                                        || ExecutionTraceStatus.AGENT_INACTIVE.equals(
                                            trace.getStatus())))))
        .orElse(Boolean.TRUE)
        .booleanValue(); // If there are no traces, return true by default
  }

  /**
   * Determines if an agent is a primary agent.
   *
   * <p>A primary agent is a standalone agent that is not a child of another agent and is not
   * created specifically for an inject execution.
   *
   * @param agent the agent to check
   * @return {@code true} if the agent has no parent and no associated inject
   */
  public static boolean isPrimaryAgent(Agent agent) {
    return agent.getParent() == null && agent.getInject() == null;
  }

  /**
   * Retrieves all primary agents from an endpoint.
   *
   * @param endpoint the endpoint to get primary agents from
   * @return a list of primary agents associated with the endpoint
   * @see #isPrimaryAgent(Agent)
   */
  public static List<Agent> getPrimaryAgents(Endpoint endpoint) {
    return endpoint.getAgents().stream()
        .filter(AgentUtils::isPrimaryAgent)
        .collect(Collectors.toList());
  }
}
