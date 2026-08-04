package io.openaev.service.attackpath.dto;

import io.openaev.rest.payload.form.DetectionRemediationOutput;
import java.util.List;

/**
 * One execution's full detail for the Result &amp; Terminal drawer (issue 5048), read from the
 * frozen snapshot (never the live inject). Three groups: the header (payload/agent/privilege), the
 * result (target, prevention/detection status, findings), and the terminal (command, output).
 * Credentials are masked server-side in the command, the output, and the finding values.
 */
public record AttackPathExecutionDetailDTO(
    // header
    String payloadName,
    String stepId,
    String injectId,
    String payloadId,
    String agentName,
    String agentPrivilege,
    List<AttackPathAttackPatternDTO> attackPatterns,
    List<DetectionRemediationOutput> detectionRemediations,
    // result
    String endpointKey,
    String targetHostname,
    String targetIp,
    String targetPlatform,
    String preventionStatus,
    String detectionStatus,
    String vulnerabilityStatus,
    String executedAt,
    List<AttackPathExecutionFindingItemDTO> findings,
    // the security platforms that acted (prevention/detection), resolved live from the inject's
    // expectations, with their linked alerts (A1)
    List<AttackPathSecurityPlatformDTO> securityPlatforms,
    // terminal
    String command,
    String terminalOutput,
    // The reconstructed, partially-masked command line of a network injector (NetExec, Nmap…),
    // which has no `command` snapshot of its own (see AttackPathGraphService#injectorCommandLine).
    // Null for a Command-payload-backed execution, which already has `command`.
    String injectorCommandLine) {}
