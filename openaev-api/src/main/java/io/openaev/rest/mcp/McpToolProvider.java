package io.openaev.rest.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class McpToolProvider {

  private static final Logger log = LoggerFactory.getLogger(McpToolProvider.class);
  private static final String COOKIE_NAME = "openaev_token";
  private static final String HEADER_NAME = "Authorization";
  private static final String BEARER_PREFIX = "Bearer ";

  private final ObjectMapper objectMapper;
  private final RestTemplate restTemplate;
  private final String baseUrl;

  public McpToolProvider(
      ObjectMapper objectMapper,
      @Value("${openaev.base-url:http://localhost:8080}") String baseUrl) {
    this.objectMapper = objectMapper;
    this.restTemplate = new RestTemplate();
    this.baseUrl = baseUrl;
  }

  public List<SyncToolSpecification> getToolSpecifications() {
    List<SyncToolSpecification> tools = new ArrayList<>();

    // 1. Search assets
    tools.add(buildTool(
        "search_openaev_assets",
        "Search for assets (endpoints) in OpenAEV by name, hostname, or IP address. "
            + "Assets represent machines, servers, workstations, or devices in the environment.",
        schema(Map.of(
            "search", prop("string", "Search term (name, hostname, or IP)"),
            "limit", propWithDefault("integer", "Max results (default 20)", 20)),
            List.of("search")),
        (exchange, request) -> {
          String search = strArg(request, "search");
          int limit = intArg(request, "limit", 20);
          return callSearchApi("/api/endpoints/search", search,
              List.of("asset_name", "endpoint_hostname", "endpoint_ips"), limit);
        }));

    // 2. Get asset
    tools.add(buildTool(
        "get_openaev_asset",
        "Get full details of a specific asset (endpoint) by its OpenAEV ID.",
        schema(Map.of(
            "asset_id", prop("string", "The asset's OpenAEV ID")),
            List.of("asset_id")),
        (exchange, request) -> callGetApi("/api/endpoints/" + strArg(request, "asset_id"))));

    // 3. Search asset groups
    tools.add(buildTool(
        "search_openaev_asset_groups",
        "Search for asset groups in OpenAEV by name. Asset groups are collections of assets.",
        schema(Map.of(
            "search", prop("string", "Search term (group name)"),
            "limit", propWithDefault("integer", "Max results (default 20)", 20)),
            List.of("search")),
        (exchange, request) -> {
          String search = strArg(request, "search");
          int limit = intArg(request, "limit", 20);
          return callSearchApi("/api/asset_groups/search", search,
              List.of("asset_group_name"), limit);
        }));

    // 4. Get asset group
    tools.add(buildTool(
        "get_openaev_asset_group",
        "Get full details of a specific asset group by ID.",
        schema(Map.of(
            "group_id", prop("string", "The asset group's ID")),
            List.of("group_id")),
        (exchange, request) ->
            callGetApi("/api/asset_groups/" + strArg(request, "group_id"))));

    // 5. Search teams
    tools.add(buildTool(
        "search_openaev_teams",
        "Search for teams in OpenAEV by name. Teams group players for exercises and scenarios.",
        schema(Map.of(
            "search", prop("string", "Search term (team name)"),
            "limit", propWithDefault("integer", "Max results (default 20)", 20)),
            List.of("search")),
        (exchange, request) -> {
          String search = strArg(request, "search");
          int limit = intArg(request, "limit", 20);
          return callSearchApi("/api/teams/search", search, List.of("team_name"), limit);
        }));

    // 6. Get team
    tools.add(buildTool(
        "get_openaev_team",
        "Get full details of a specific team by ID.",
        schema(Map.of(
            "team_id", prop("string", "The team's ID")),
            List.of("team_id")),
        (exchange, request) -> callGetApi("/api/teams/" + strArg(request, "team_id"))));

    // 7. Search players
    tools.add(buildTool(
        "search_openaev_players",
        "Search for players (users/people) in OpenAEV by email, first name, or last name.",
        schema(Map.of(
            "search", prop("string", "Search term (email, first name, last name)"),
            "limit", propWithDefault("integer", "Max results (default 20)", 20)),
            List.of("search")),
        (exchange, request) -> {
          String search = strArg(request, "search");
          int limit = intArg(request, "limit", 20);
          return callSearchApi("/api/players/search", search,
              List.of("user_email", "user_firstname", "user_lastname"), limit);
        }));

    // 8. Search attack patterns
    tools.add(buildTool(
        "search_openaev_attack_patterns",
        "Search MITRE ATT&CK attack patterns in OpenAEV. "
            + "Returns technique IDs, names, descriptions, and platforms.",
        schema(Map.of(
            "search", propWithDefault("string", "Search term (technique name or ID like T1059)", ""),
            "limit", propWithDefault("integer", "Max results (default 20)", 20)),
            List.of()),
        (exchange, request) -> {
          String search = strArg(request, "search", "");
          int limit = intArg(request, "limit", 20);
          return callTextSearchApi("/api/attack_patterns/search", search, limit);
        }));

    // 9. Get attack pattern
    tools.add(buildTool(
        "get_openaev_attack_pattern",
        "Get full details of a specific MITRE ATT&CK attack pattern by ID.",
        schema(Map.of(
            "attack_pattern_id", prop("string", "The attack pattern's OpenAEV ID")),
            List.of("attack_pattern_id")),
        (exchange, request) ->
            callGetApi("/api/attack_patterns/" + strArg(request, "attack_pattern_id"))));

    // 10. Search scenarios
    tools.add(buildTool(
        "search_openaev_scenarios",
        "Search for adversary simulation scenarios in OpenAEV or list all scenarios.",
        schema(Map.of(
            "search", propWithDefault("string", "Search term (optional)", ""),
            "limit", propWithDefault("integer", "Max results (default 20)", 20)),
            List.of()),
        (exchange, request) -> {
          String search = strArg(request, "search", "");
          int limit = intArg(request, "limit", 20);
          return callTextSearchApi("/api/scenarios/search", search, limit);
        }));

    // 11. Get scenario
    tools.add(buildTool(
        "get_openaev_scenario",
        "Get full details of a specific scenario by ID.",
        schema(Map.of(
            "scenario_id", prop("string", "The scenario's OpenAEV ID")),
            List.of("scenario_id")),
        (exchange, request) ->
            callGetApi("/api/scenarios/" + strArg(request, "scenario_id"))));

    // 12. Create scenario
    tools.add(buildTool(
        "create_openaev_scenario",
        "Create a new adversary simulation scenario in OpenAEV. "
            + "A scenario defines an attack storyline with injects (steps), targets, and teams.",
        schema(Map.of(
            "name", prop("string", "Scenario name"),
            "description", propWithDefault("string", "Detailed description", ""),
            "subtitle", propWithDefault("string", "Short subtitle", ""),
            "category", propWithDefault("string",
                "Category: global-crisis, attack-scenario, media-pressure, data-exfiltration, "
                    + "capture-the-flag, vulnerability-exploitation, lateral-movement, url-filtering",
                "attack-scenario"),
            "main_focus", propWithDefault("string",
                "Main focus: incident-response, endpoint-protection, web-filtering, "
                    + "standard-operating-procedure, crisis-communication, strategic-reaction",
                "incident-response"),
            "severity", propEnum("string", "Severity level",
                List.of("low", "medium", "high", "critical"), "high"),
            "tags", propArray("string", "Tag IDs to apply")),
            List.of("name")),
        (exchange, request) -> {
          ObjectNode body = objectMapper.createObjectNode();
          body.put("scenario_name", strArg(request, "name"));
          body.put("scenario_description", strArg(request, "description", ""));
          body.put("scenario_subtitle", strArg(request, "subtitle", ""));
          body.put("scenario_category", strArg(request, "category", "attack-scenario"));
          body.put("scenario_main_focus", strArg(request, "main_focus", "incident-response"));
          body.put("scenario_severity", strArg(request, "severity", "high"));
          putArrayArg(body, "scenario_tags", request, "tags");
          return callPostApi("/api/scenarios", body);
        }));

    // 13. Update scenario
    tools.add(buildTool(
        "update_openaev_scenario",
        "Update an existing scenario (name, description, category, severity, etc.).",
        schema(Map.of(
            "scenario_id", prop("string", "Scenario ID to update"),
            "name", prop("string", "New name (optional)"),
            "description", prop("string", "New description (optional)"),
            "subtitle", prop("string", "New subtitle (optional)"),
            "category", prop("string", "New category (optional)"),
            "main_focus", prop("string", "New main focus (optional)"),
            "severity", propEnum("string", "New severity (optional)",
                List.of("low", "medium", "high", "critical"), null)),
            List.of("scenario_id")),
        (exchange, request) -> {
          ObjectNode body = objectMapper.createObjectNode();
          putIfPresent(body, "scenario_name", request, "name");
          putIfPresent(body, "scenario_description", request, "description");
          putIfPresent(body, "scenario_subtitle", request, "subtitle");
          putIfPresent(body, "scenario_category", request, "category");
          putIfPresent(body, "scenario_main_focus", request, "main_focus");
          putIfPresent(body, "scenario_severity", request, "severity");
          return callPutApi("/api/scenarios/" + strArg(request, "scenario_id"), body);
        }));

    // 14. Delete scenario
    tools.add(buildTool(
        "delete_openaev_scenario",
        "Delete a scenario from OpenAEV. This is permanent.",
        schema(Map.of(
            "scenario_id", prop("string", "Scenario ID to delete")),
            List.of("scenario_id")),
        (exchange, request) ->
            callDeleteApi("/api/scenarios/" + strArg(request, "scenario_id"))));

    // 15. Create payload
    Map<String, Map<String, Object>> payloadProps = new LinkedHashMap<>();
    payloadProps.put("payload_type", propEnum("string", "Payload type",
        List.of("Command", "Executable", "FileDrop", "DnsResolution", "NetworkTraffic"), null));
    payloadProps.put("name", prop("string", "Payload name"));
    payloadProps.put("platforms", propArrayEnum("string", "Target platforms",
        List.of("Linux", "Windows", "MacOS", "Container", "Service", "Generic", "Internal")));
    payloadProps.put("domain_ids", propArray("string",
        "Domain IDs (security domains). Use list_openaev_domains to get available IDs. Required by OpenAEV."));
    payloadProps.put("description", propWithDefault("string", "Payload description", ""));
    payloadProps.put("command_executor", propEnum("string",
        "Command executor (for Command type): psh, cmd, bash, sh",
        List.of("psh", "cmd", "bash", "sh"), null));
    payloadProps.put("command_content", prop("string", "Shell command to execute (for Command type)"));
    payloadProps.put("dns_resolution_hostname",
        prop("string", "Hostname to resolve (for DnsResolution type)"));
    payloadProps.put("attack_pattern_ids", propArray("string", "Attack pattern IDs to link"));
    payloadProps.put("tag_ids", propArray("string",
        "Tag IDs to attach. Use list_openaev_tags to get available IDs."));
    payloadProps.put("cleanup_executor", prop("string", "Cleanup command executor (optional)"));
    payloadProps.put("cleanup_command",
        prop("string", "Cleanup command to run after payload (optional)"));
    tools.add(buildTool(
        "create_openaev_payload",
        "Create a payload for use in injects and atomic tests. "
            + "Payload types: Command (shell command), DnsResolution (DNS lookup), "
            + "Executable, FileDrop, NetworkTraffic.",
        schema(payloadProps,
            List.of("payload_type", "name", "platforms", "domain_ids")),
        (exchange, request) -> {
          ObjectNode body = objectMapper.createObjectNode();
          body.put("payload_type", strArg(request, "payload_type"));
          body.put("payload_name", strArg(request, "name"));
          body.put("payload_description", strArg(request, "description", ""));
          putArrayArg(body, "payload_platforms", request, "platforms");
          body.put("payload_source", "MANUAL");
          body.put("payload_status", "VERIFIED");
          body.put("payload_execution_arch", "ALL_ARCHITECTURES");
          body.putArray("payload_expectations").add("PREVENTION").add("DETECTION");
          putArrayArg(body, "payload_attack_patterns", request, "attack_pattern_ids");
          putArrayArg(body, "payload_domains", request, "domain_ids");
          putArrayArg(body, "payload_tags", request, "tag_ids");
          String payloadType = strArg(request, "payload_type");
          if ("Command".equals(payloadType)) {
            body.put("command_executor", strArg(request, "command_executor", ""));
            body.put("command_content", strArg(request, "command_content", ""));
          }
          if ("DnsResolution".equals(payloadType)) {
            body.put("dns_resolution_hostname", strArg(request, "dns_resolution_hostname", ""));
          }
          String cleanupExec = strArg(request, "cleanup_executor", "");
          if (!cleanupExec.isEmpty()) {
            body.put("payload_cleanup_executor", cleanupExec);
          }
          String cleanupCmd = strArg(request, "cleanup_command", "");
          if (!cleanupCmd.isEmpty()) {
            body.put("payload_cleanup_command", cleanupCmd);
          }
          return callPostApi("/api/payloads", body);
        }));

    // 16. Get payload
    tools.add(buildTool(
        "get_openaev_payload",
        "Get full details of a payload by ID.",
        schema(Map.of(
            "payload_id", prop("string", "Payload ID")),
            List.of("payload_id")),
        (exchange, request) ->
            callGetApi("/api/payloads/" + strArg(request, "payload_id"))));

    // 17. Search payloads
    tools.add(buildTool(
        "search_openaev_payloads",
        "Search for payloads in OpenAEV or list all.",
        schema(Map.of(
            "search", propWithDefault("string", "Search term (optional)", ""),
            "limit", propWithDefault("integer", "Max results (default 20)", 20)),
            List.of()),
        (exchange, request) -> {
          String search = strArg(request, "search", "");
          int limit = intArg(request, "limit", 20);
          return callTextSearchApi("/api/payloads/search", search, limit);
        }));

    // 18. Add inject to scenario
    tools.add(buildTool(
        "add_openaev_inject_to_scenario",
        "Add an inject (action step) to a scenario. Each inject represents one step "
            + "in the attack simulation (e.g., phishing email, lateral movement, data exfiltration). "
            + "Requires an injector contract ID that defines the inject type.",
        schema(Map.of(
            "scenario_id", prop("string", "Target scenario ID"),
            "title", prop("string", "Inject title"),
            "injector_contract_id", prop("string", "Injector contract ID defining the inject type"),
            "description", propWithDefault("string", "Inject description", ""),
            "delay_seconds", propWithDefault("integer",
                "Delay before execution in seconds (0 = immediate)", 0),
            "team_ids", propArray("string", "Target team IDs"),
            "asset_ids", propArray("string", "Target asset IDs"),
            "asset_group_ids", propArray("string", "Target asset group IDs"),
            "content", prop("string", "Inject content as JSON string (contract-specific fields)")),
            List.of("scenario_id", "title", "injector_contract_id")),
        (exchange, request) -> {
          ObjectNode body = objectMapper.createObjectNode();
          body.put("inject_title", strArg(request, "title"));
          body.put("inject_description", strArg(request, "description", ""));
          body.put("inject_injector_contract", strArg(request, "injector_contract_id"));
          body.put("inject_depends_duration", intArg(request, "delay_seconds", 0));
          putArrayArg(body, "inject_teams", request, "team_ids");
          putArrayArg(body, "inject_assets", request, "asset_ids");
          putArrayArg(body, "inject_asset_groups", request, "asset_group_ids");
          String content = strArg(request, "content", "");
          if (!content.isEmpty()) {
            try {
              body.set("inject_content", objectMapper.readTree(content));
            } catch (JsonProcessingException e) {
              ObjectNode wrapper = objectMapper.createObjectNode();
              wrapper.put("content", content);
              body.set("inject_content", wrapper);
            }
          } else {
            body.set("inject_content", objectMapper.createObjectNode());
          }
          String scenarioId = strArg(request, "scenario_id");
          return callPostApi("/api/scenarios/" + scenarioId + "/injects", body);
        }));

    // 19. Search injector contracts
    tools.add(buildTool(
        "search_openaev_injector_contracts",
        "Search for injector contracts in OpenAEV. Injector contracts define "
            + "available inject types (email, command execution, HTTP request, etc.). "
            + "Use this to find the right contract ID before adding injects to scenarios.",
        schema(Map.of(
            "search", propWithDefault("string", "Search term (optional)", ""),
            "limit", propWithDefault("integer", "Max results (default 30)", 30)),
            List.of()),
        (exchange, request) -> {
          String search = strArg(request, "search", "");
          int limit = intArg(request, "limit", 30);
          return callTextSearchApi("/api/injector_contracts/search", search, limit);
        }));

    // 20. Get injector contract
    tools.add(buildTool(
        "get_openaev_injector_contract",
        "Get full details of an injector contract by ID.",
        schema(Map.of(
            "contract_id", prop("string", "Injector contract ID")),
            List.of("contract_id")),
        (exchange, request) ->
            callGetApi("/api/injector_contracts/" + strArg(request, "contract_id"))));

    // 21. Create atomic testing
    tools.add(buildTool(
        "create_openaev_atomic_testing",
        "Create an atomic test in OpenAEV. Atomic tests execute a single inject "
            + "against specific assets to validate detection/prevention capabilities. "
            + "Requires an injector contract ID.",
        schema(Map.of(
            "title", prop("string", "Atomic test title"),
            "injector_contract_id", prop("string", "Injector contract ID"),
            "description", propWithDefault("string", "Test description", ""),
            "asset_ids", propArray("string", "Target asset IDs"),
            "asset_group_ids", propArray("string", "Target asset group IDs"),
            "content", prop("string", "Test content as JSON string (contract-specific)")),
            List.of("title", "injector_contract_id")),
        (exchange, request) -> {
          ObjectNode body = objectMapper.createObjectNode();
          body.put("inject_title", strArg(request, "title"));
          body.put("inject_description", strArg(request, "description", ""));
          body.put("inject_injector_contract", strArg(request, "injector_contract_id"));
          putArrayArg(body, "inject_assets", request, "asset_ids");
          putArrayArg(body, "inject_asset_groups", request, "asset_group_ids");
          body.putArray("inject_teams");
          body.put("inject_all_teams", false);
          String content = strArg(request, "content", "");
          if (!content.isEmpty()) {
            try {
              body.set("inject_content", objectMapper.readTree(content));
            } catch (JsonProcessingException e) {
              ObjectNode wrapper = objectMapper.createObjectNode();
              wrapper.put("content", content);
              body.set("inject_content", wrapper);
            }
          } else {
            body.set("inject_content", objectMapper.createObjectNode());
          }
          return callPostApi("/api/atomic-testings", body);
        }));

    // 22. Search findings
    tools.add(buildTool(
        "search_openaev_findings",
        "Search for security findings in OpenAEV. Findings are detected issues on assets.",
        schema(Map.of(
            "search", propWithDefault("string", "Search term (optional)", ""),
            "limit", propWithDefault("integer", "Max results (default 20)", 20)),
            List.of()),
        (exchange, request) -> {
          String search = strArg(request, "search", "");
          int limit = intArg(request, "limit", 20);
          return callTextSearchApi("/api/findings/search?distinct=true", search, limit);
        }));

    // 23. Get vulnerability by CVE
    tools.add(buildTool(
        "get_openaev_vulnerability",
        "Look up a vulnerability in OpenAEV by its CVE ID (e.g., CVE-2024-1234).",
        schema(Map.of(
            "cve_id", prop("string", "CVE ID (e.g., CVE-2024-1234)")),
            List.of("cve_id")),
        (exchange, request) ->
            callGetApi("/api/vulnerabilities/external-id/" + strArg(request, "cve_id"))));

    // 24. Full text search
    tools.add(buildTool(
        "full_text_search_openaev",
        "Full-text search across all OpenAEV entity types. "
            + "Returns counts per entity type (assets, teams, scenarios, etc.). "
            + "Use this for broad discovery before drilling into specific entity types.",
        schema(Map.of(
            "search", prop("string", "Search term")),
            List.of("search")),
        (exchange, request) -> {
          ObjectNode body = objectMapper.createObjectNode();
          body.put("searchTerm", strArg(request, "search"));
          return callPostApi("/api/fulltextsearch", body);
        }));

    // 25. List kill chain phases
    tools.add(buildTool(
        "list_openaev_kill_chain_phases",
        "List all kill chain phases in OpenAEV (MITRE ATT&CK, Lockheed Martin, etc.).",
        schema(Map.of(), List.of()),
        (exchange, request) -> callGetApi("/api/kill_chain_phases")));

    // 26. List tags
    tools.add(buildTool(
        "list_openaev_tags",
        "List all tags available in OpenAEV for categorizing entities.",
        schema(Map.of(), List.of()),
        (exchange, request) -> callGetApi("/api/tags")));

    // 27. List domains
    tools.add(buildTool(
        "list_openaev_domains",
        "List all security domains available in OpenAEV. "
            + "Domain IDs are required when creating payloads.",
        schema(Map.of(), List.of()),
        (exchange, request) -> callGetApi("/api/domains")));

    // 28. Search exercises
    tools.add(buildTool(
        "search_openaev_exercises",
        "Search for exercises (simulations) in OpenAEV or list all.",
        schema(Map.of(
            "search", propWithDefault("string", "Search term (optional)", ""),
            "limit", propWithDefault("integer", "Max results (default 20)", 20)),
            List.of()),
        (exchange, request) -> {
          String search = strArg(request, "search", "");
          int limit = intArg(request, "limit", 20);
          return callTextSearchApi("/api/exercises/search", search, limit);
        }));

    // 29. Get exercise
    tools.add(buildTool(
        "get_openaev_exercise",
        "Get full details of a specific exercise/simulation by ID.",
        schema(Map.of(
            "exercise_id", prop("string", "Exercise ID")),
            List.of("exercise_id")),
        (exchange, request) ->
            callGetApi("/api/exercises/" + strArg(request, "exercise_id"))));

    return tools;
  }

  // ---------------------------------------------------------------------------
  // Internal HTTP helpers
  // ---------------------------------------------------------------------------

  private String extractBearerToken() {
    ServletRequestAttributes attrs =
        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    if (attrs == null) {
      return null;
    }
    HttpServletRequest request = attrs.getRequest();
    String header = request.getHeader(HEADER_NAME);
    if (header != null && !header.isEmpty()) {
      return header.startsWith(BEARER_PREFIX) ? header : BEARER_PREFIX + header;
    }
    Cookie[] cookies =
        Optional.ofNullable(request.getCookies()).orElse(new Cookie[0]);
    return Arrays.stream(cookies)
        .filter(c -> COOKIE_NAME.equals(c.getName()))
        .findFirst()
        .map(c -> BEARER_PREFIX + c.getValue())
        .orElse(null);
  }

  private HttpHeaders buildHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    String token = extractBearerToken();
    if (token != null) {
      headers.set(HEADER_NAME, token);
    }
    return headers;
  }

  private CallToolResult callGetApi(String path) {
    try {
      HttpEntity<Void> entity = new HttpEntity<>(buildHeaders());
      ResponseEntity<String> resp =
          restTemplate.exchange(baseUrl + path, HttpMethod.GET, entity, String.class);
      return textResult(resp.getBody());
    } catch (RestClientException e) {
      log.warn("MCP tool GET {} failed: {}", path, e.getMessage());
      return errorResult("Request failed: " + e.getMessage());
    }
  }

  private CallToolResult callPostApi(String path, ObjectNode body) {
    try {
      HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(body), buildHeaders());
      ResponseEntity<String> resp =
          restTemplate.exchange(baseUrl + path, HttpMethod.POST, entity, String.class);
      return textResult(resp.getBody());
    } catch (RestClientException | JsonProcessingException e) {
      log.warn("MCP tool POST {} failed: {}", path, e.getMessage());
      return errorResult("Request failed: " + e.getMessage());
    }
  }

  private CallToolResult callPutApi(String path, ObjectNode body) {
    try {
      HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(body), buildHeaders());
      ResponseEntity<String> resp =
          restTemplate.exchange(baseUrl + path, HttpMethod.PUT, entity, String.class);
      return textResult(resp.getBody());
    } catch (RestClientException | JsonProcessingException e) {
      log.warn("MCP tool PUT {} failed: {}", path, e.getMessage());
      return errorResult("Request failed: " + e.getMessage());
    }
  }

  private CallToolResult callDeleteApi(String path) {
    try {
      HttpEntity<Void> entity = new HttpEntity<>(buildHeaders());
      restTemplate.exchange(baseUrl + path, HttpMethod.DELETE, entity, String.class);
      return textResult("Deleted successfully.");
    } catch (RestClientException e) {
      log.warn("MCP tool DELETE {} failed: {}", path, e.getMessage());
      return errorResult("Request failed: " + e.getMessage());
    }
  }

  private CallToolResult callSearchApi(
      String path, String search, List<String> filterKeys, int limit) {
    ObjectNode body = objectMapper.createObjectNode();
    body.put("page", 0);
    body.put("size", Math.min(limit, 50));
    if (search != null && !search.isEmpty()) {
      ObjectNode filterGroup = objectMapper.createObjectNode();
      filterGroup.put("mode", "or");
      var filtersArray = filterGroup.putArray("filters");
      for (String key : filterKeys) {
        ObjectNode filter = objectMapper.createObjectNode();
        filter.put("key", key);
        filter.put("operator", "contains");
        filter.putArray("values").add(search);
        filtersArray.add(filter);
      }
      body.set("filterGroup", filterGroup);
    }
    return callPostApi(path, body);
  }

  private CallToolResult callTextSearchApi(String path, String search, int limit) {
    ObjectNode body = objectMapper.createObjectNode();
    body.put("page", 0);
    body.put("size", Math.min(limit, 100));
    if (search != null && !search.isEmpty()) {
      body.put("textSearch", search);
    }
    return callPostApi(path, body);
  }

  // ---------------------------------------------------------------------------
  // Tool builder helpers
  // ---------------------------------------------------------------------------

  @FunctionalInterface
  private interface ToolHandler {
    CallToolResult handle(
        io.modelcontextprotocol.server.McpSyncServerExchange exchange,
        McpSchema.CallToolRequest request);
  }

  private SyncToolSpecification buildTool(
      String name, String description, McpSchema.JsonSchema inputSchema, ToolHandler handler) {
    return SyncToolSpecification.builder()
        .tool(Tool.builder()
            .name(name)
            .description(description)
            .inputSchema(inputSchema)
            .build())
        .callHandler((exchange, request) -> {
          try {
            return handler.handle(exchange, request);
          } catch (Exception e) {
            log.error("MCP tool '{}' failed: {}", name, e.getMessage(), e);
            return errorResult("Tool execution failed: " + e.getMessage());
          }
        })
        .build();
  }

  // ---------------------------------------------------------------------------
  // Schema helpers
  // ---------------------------------------------------------------------------

  private McpSchema.JsonSchema schema(
      Map<String, Map<String, Object>> properties, List<String> required) {
    Map<String, Object> props = new LinkedHashMap<>(properties);
    return new McpSchema.JsonSchema("object", props, required, null);
  }

  private Map<String, Object> prop(String type, String description) {
    Map<String, Object> m = new HashMap<>();
    m.put("type", type);
    m.put("description", description);
    return m;
  }

  private Map<String, Object> propWithDefault(String type, String description, Object defaultValue) {
    Map<String, Object> m = new HashMap<>();
    m.put("type", type);
    m.put("description", description);
    m.put("default", defaultValue);
    return m;
  }

  private Map<String, Object> propEnum(
      String type, String description, List<String> enumValues, String defaultValue) {
    Map<String, Object> m = new HashMap<>();
    m.put("type", type);
    m.put("description", description);
    m.put("enum", enumValues);
    if (defaultValue != null) {
      m.put("default", defaultValue);
    }
    return m;
  }

  private Map<String, Object> propArray(String itemType, String description) {
    Map<String, Object> m = new HashMap<>();
    m.put("type", "array");
    m.put("description", description);
    m.put("items", Map.of("type", itemType));
    return m;
  }

  private Map<String, Object> propArrayEnum(
      String itemType, String description, List<String> enumValues) {
    Map<String, Object> m = new HashMap<>();
    m.put("type", "array");
    m.put("description", description);
    m.put("items", Map.of("type", itemType, "enum", enumValues));
    return m;
  }

  // ---------------------------------------------------------------------------
  // Argument extraction helpers
  // ---------------------------------------------------------------------------

  private String strArg(McpSchema.CallToolRequest request, String key) {
    Object val = request.arguments().get(key);
    return val != null ? val.toString() : "";
  }

  private String strArg(McpSchema.CallToolRequest request, String key, String defaultValue) {
    Object val = request.arguments().get(key);
    return val != null ? val.toString() : defaultValue;
  }

  private int intArg(McpSchema.CallToolRequest request, String key, int defaultValue) {
    Object val = request.arguments().get(key);
    if (val instanceof Number n) {
      return n.intValue();
    }
    if (val instanceof String s) {
      try {
        return Integer.parseInt(s);
      } catch (NumberFormatException e) {
        return defaultValue;
      }
    }
    return defaultValue;
  }

  private void putIfPresent(
      ObjectNode body, String jsonKey, McpSchema.CallToolRequest request, String argKey) {
    Object val = request.arguments().get(argKey);
    if (val != null) {
      body.put(jsonKey, val.toString());
    }
  }

  @SuppressWarnings("unchecked")
  private void putArrayArg(
      ObjectNode body, String jsonKey, McpSchema.CallToolRequest request, String argKey) {
    Object val = request.arguments().get(argKey);
    var arr = body.putArray(jsonKey);
    if (val instanceof List<?> list) {
      list.forEach(item -> arr.add(item.toString()));
    }
  }

  // ---------------------------------------------------------------------------
  // Result helpers
  // ---------------------------------------------------------------------------

  private static CallToolResult textResult(String text) {
    return CallToolResult.builder()
        .content(List.of(new McpSchema.TextContent(text != null ? text : "No data returned.")))
        .build();
  }

  private static CallToolResult errorResult(String message) {
    return CallToolResult.builder()
        .content(List.of(new McpSchema.TextContent(message)))
        .isError(true)
        .build();
  }
}
