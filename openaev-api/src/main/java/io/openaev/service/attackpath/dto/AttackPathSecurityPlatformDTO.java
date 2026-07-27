package io.openaev.service.attackpath.dto;

import java.util.List;

/**
 * One security platform that acted on an execution, for the Result &amp; Terminal drawer (A1).
 * Resolved live from the execution's inject expectations (like the ATT&amp;CK techniques and
 * detection remediations already are), scoped to the execution's agent or asset. A platform can
 * appear once per bucket: it may both prevent and detect.
 */
public record AttackPathSecurityPlatformDTO(
    String
        platformType, // EDR | XDR | SIEM (SecurityPlatform.SECURITY_PLATFORM_TYPE), null if unknown
    String platformName,
    String bucket, // "prevention" | "detection"
    String status, // SUCCESS | FAILED | PARTIAL | PENDING | UNKNOWN
    String detectedAt, // ISO instant, the expectation result date
    List<AttackPathAlertDTO> alerts) {}
