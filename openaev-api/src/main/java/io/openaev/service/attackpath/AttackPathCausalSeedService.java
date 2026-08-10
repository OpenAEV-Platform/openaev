package io.openaev.service.attackpath;

import io.openaev.database.model.Agent;
import io.openaev.database.model.Condition;
import io.openaev.database.model.ConditionType;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Injector;
import io.openaev.database.model.PrimitiveType;
import io.openaev.database.model.Step;
import io.openaev.database.model.StepActionClass;
import io.openaev.database.model.StepStatus;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.Workflow;
import io.openaev.database.model.WorkflowStatus;
import io.openaev.database.model.attackpath.AttackPathExecution;
import io.openaev.database.repository.ConditionRepository;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.StepRepository;
import io.openaev.database.repository.TenantRepository;
import io.openaev.database.repository.WorkflowRepository;
import io.openaev.database.repository.attackpath.AttackPathExecutionRepository;
import io.openaev.service.attackpath.dto.AttackPathReplayStepDTO;
import io.openaev.service.attackpath.ingestion.AttackPathFindingWriter;
import io.openaev.service.attackpath.ingestion.AttackPathVersionService;
import io.openaev.service.chaining.ConditionService;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Byte-faithful, causal, live-capable attack-path seed. Writes through the engine's OWN mechanisms
 * (AttackPathExecution entities built via the ingestion setters, AttackPathFindingWriter, and the
 * chaining condition model) so the rendered graph is identical to a real run. Distinct from the
 * static {@link AttackPathSeedService}, which keeps feeding the aggregated view.
 */
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class AttackPathCausalSeedService {

  private final ExerciseRepository exerciseRepository;
  private final WorkflowRepository workflowRepository;
  private final StepRepository stepRepository;
  private final ConditionRepository conditionRepository;
  private final ConditionService conditionService;
  private final AttackPathExecutionRepository executionRepository;
  private final AttackPathFindingWriter findingWriter;
  private final AttackPathVersionService versionService;
  private final TenantRepository tenantRepository;
  private final EntityManager entityManager;

  /** The number of kill-chain stages the ransomware scenario replays, one version bump each. */
  private static final int RANSOMWARE_STAGES = 8;

  /**
   * Create the minimal condition model (a step template carrying a {@code port EQ 445} condition)
   * and one execution referencing it, under the given simulation + tenant, so {@code buildGraph}
   * resolves that step's consumed key onto the execution: the {@code step_template_id} &lt;-&gt;
   * condition linkage the rest of the seed relies on.
   */
  public void seedCausalMinimal(String simulationId, String tenantId) {
    Tenant tenant = tenantRepository.getReferenceById(tenantId);
    Step template =
        conditionedTemplate(tenant, simulationId, PrimitiveType.Port, ConditionType.EQ, "445");

    AttackPathExecution execution = new AttackPathExecution();
    execution.setTenant(tenant);
    execution.setSimulationId(simulationId);
    execution.setStepTemplateId(template.getId());
    execution.setSourceKind("INJECTOR");
    execution.setSourceInjector("nmap");
    execution.setTargetKind("ASSET");
    execution.setTargetKey("ep-dc-01");
    execution.setTargetAssetId("ep-dc-01");
    execution.setExecutedAt(Instant.now());
    execution.setRowVersion(1L);
    executionRepository.save(execution);
  }

  /**
   * Build ONE byte-faithful execution through the engine's OWN setters (deterministic {@code
   * AttackPathIds} id + frozen columns computed by production code), under the given simulation +
   * tenant.
   */
  public void seedFaithfulExecution(String simulationId, String tenantId) {
    Tenant tenant = tenantRepository.getReferenceById(tenantId);
    Inject inject =
        inject(
            simulationId + "-inject-nmap-castelback",
            "Nmap portscan",
            tenant,
            simulationId,
            injector("nmap", "Nmap", "openbas_nmap"));
    Step template = new Step();
    template.setId(simulationId + "-template-nmap");
    Endpoint endpoint =
        endpoint(simulationId + "-endpoint-castelback", "castelback", "20.224.192.102");
    executionRepository.save(
        injectorToAssetExecution(
            inject, stepInstance(simulationId + "-step-nmap", template), endpoint, 1L));
  }

  /**
   * Seed the minimal causal chain: a recon execution that produces a portscan finding, and the
   * exploit execution that consumes {@code port EQ 445} as an agent pivot, so buildGraph resolves
   * the finding as the exploit's matched producer (the connected source-to-destination edge).
   */
  public void seedMinimalCausalChain(String simulationId, String tenantId) {
    Tenant tenant = tenantRepository.getReferenceById(tenantId);
    long version = versionService.bump(simulationId, tenantId);

    Endpoint castelback =
        endpoint(simulationId + "-endpoint-castelback", "castelback", "20.224.192.102");
    Endpoint winterfell = endpoint(simulationId + "-endpoint-winterfell", "winterfell", "10.0.0.7");
    // Producer strictly before consumer: what buildGraph requires to draw the causal edge.
    Instant reconAt = Instant.now().minusSeconds(60);
    Instant exploitAt = reconAt.plusSeconds(1);

    // Recon: an injector portscan of castelback, on its own step template (no condition consumed).
    Step reconTemplate = new Step();
    reconTemplate.setId(simulationId + "-recon-template");
    Inject reconInject =
        inject(
            simulationId + "-inject-nmap",
            "Nmap portscan",
            tenant,
            simulationId,
            injector("nmap", "Nmap", "openbas_nmap"));
    AttackPathExecution recon =
        injectorToAssetExecution(
            reconInject,
            stepInstance(simulationId + "-recon-step", reconTemplate),
            castelback,
            version);
    recon.setExecutedAt(reconAt);

    // Exploit: NetExec launched FROM castelback's agent, gated by `port EQ 445`, reaching
    // winterfell. Its template is a distinct, conditioned one, so it consumes the key the recon
    // finding satisfies, and castelback (recon target + exploit source) collapses to one node.
    Step exploitTemplate =
        conditionedTemplate(tenant, simulationId, PrimitiveType.Port, ConditionType.EQ, "445");
    Inject exploitInject =
        inject(
            simulationId + "-inject-netexec",
            "NetExec SMB - Share Listing",
            tenant,
            simulationId,
            injector("netexec", "NetExec", "openbas_netexec"));
    Agent agent = agentOn(castelback, simulationId + "-agent-castelback");
    AttackPathExecution exploit =
        agentPivotExecution(
            exploitInject,
            stepInstance(simulationId + "-exploit-step", exploitTemplate),
            agent,
            castelback,
            winterfell,
            version);
    exploit.setExecutedAt(exploitAt);

    executionRepository.save(recon);
    executionRepository.save(exploit);
    // Push the JPA inserts (tenant + executions) to the DB before the raw-JDBC finding/link writes,
    // whose foreign keys (tenant_id, execution_id) would otherwise not see them in this
    // transaction.
    entityManager.flush();

    // The produced portscan finding, linked to the recon execution that produced it.
    String value = AttackPathSeedFindingValues.portscan("20.224.192.102", 445, "microsoft-ds");
    String findingId =
        AttackPathIds.findingRow(simulationId, "portscan", "portscan", value, castelback.getId());
    findingWriter.insertFindings(
        List.of(
            new AttackPathFindingWriter.FindingRow(
                findingId,
                tenantId,
                simulationId,
                "portscan",
                "portscan",
                value,
                castelback.getId(),
                null,
                castelback.getId(),
                true)),
        version);
    findingWriter.insertLinks(List.of(new AttackPathFindingWriter.Link(recon.getId(), findingId)));
  }

  /**
   * Seed a realistic multi-hop chain (recon -&gt; SMB exploit -&gt; credential dump -&gt; lateral
   * movement -&gt; DC): each pivot hop consumes the finding the previous hop produced, so
   * buildGraph draws one connected source-to-destination path across several endpoints.
   */
  public String seedRealisticChain(String tenantId) {
    Tenant tenant = tenantRepository.getReferenceById(tenantId);
    Workflow workflow = demoSimulationWorkflow(tenant, "Attack path demo - recon to DC");
    String simulationId = workflow.getSimulation().getId();
    long version = versionService.bump(simulationId, tenantId);
    Instant base = Instant.now().minusSeconds(300);

    Endpoint castelback = endpoint(simulationId + "-ep-castelback", "castelback", "20.224.192.102");
    Endpoint winterfell = endpoint(simulationId + "-ep-winterfell", "winterfell", "10.0.0.7");
    Endpoint kingslanding = endpoint(simulationId + "-ep-kingslanding", "kingslanding", "10.0.0.9");
    Endpoint dc = endpoint(simulationId + "-ep-dc01", "dc01", "10.0.0.10");

    List<AttackPathExecution> executions = new ArrayList<>();
    List<AttackPathFindingWriter.FindingRow> findingRows = new ArrayList<>();
    List<AttackPathFindingWriter.Link> links = new ArrayList<>();

    // Hop 0 (recon): an injector portscan of castelback, producing the port-445 finding the first
    // pivot waits on. Its template carries no condition (nothing to consume yet).
    Step reconTemplate = new Step();
    reconTemplate.setId(simulationId + "-tmpl-recon");
    Inject nmapInject =
        inject(
            simulationId + "-inject-nmap",
            "Nmap portscan",
            tenant,
            simulationId,
            injector("nmap", "Nmap", "openbas_nmap"));
    AttackPathExecution recon =
        injectorToAssetExecution(
            nmapInject,
            stepInstance(simulationId + "-step-recon", reconTemplate),
            castelback,
            version);
    recon.setExecutedAt(base);
    executions.add(recon);
    addFinding(
        findingRows,
        links,
        recon,
        simulationId,
        tenantId,
        "portscan",
        AttackPathSeedFindingValues.portscan("20.224.192.102", 445, "microsoft-ds"),
        castelback);

    // Hop 1 (SMB exploit): from castelback to winterfell, gated on `port EQ 445`, producing a
    // share.
    AttackPathExecution exploit =
        pivotHop(
            simulationId,
            tenant,
            workflow,
            version,
            base.plusSeconds(60),
            injector("netexec", "NetExec", "openbas_netexec"),
            "NetExec SMB - Share Listing",
            castelback,
            winterfell,
            PrimitiveType.Port,
            ConditionType.EQ,
            "445",
            "exploit");
    executions.add(exploit);
    addFinding(
        findingRows,
        links,
        exploit,
        simulationId,
        tenantId,
        "share",
        AttackPathSeedFindingValues.share("winterfell", "C$", "READ,WRITE"),
        winterfell);

    // Hop 2 (credential dump): from winterfell to kingslanding, gated on the share, producing
    // creds.
    AttackPathExecution credDump =
        pivotHop(
            simulationId,
            tenant,
            workflow,
            version,
            base.plusSeconds(120),
            injector("impacket", "Impacket", "openbas_impacket"),
            "Impacket - secretsdump",
            winterfell,
            kingslanding,
            PrimitiveType.ShareName,
            ConditionType.EQ,
            "C$",
            "creddump");
    executions.add(credDump);
    addFinding(
        findingRows,
        links,
        credDump,
        simulationId,
        tenantId,
        "credentials",
        AttackPathSeedFindingValues.credentials("administrator", "P@ssw0rd!"),
        kingslanding);

    // Hop 3 (lateral movement): from kingslanding to the DC, gated on the dumped credentials.
    AttackPathExecution lateral =
        pivotHop(
            simulationId,
            tenant,
            workflow,
            version,
            base.plusSeconds(180),
            injector("impacket", "Impacket", "openbas_impacket"),
            "Impacket - psexec (lateral movement)",
            kingslanding,
            dc,
            PrimitiveType.Username,
            ConditionType.EQ,
            "administrator",
            "lateral");
    executions.add(lateral);

    executionRepository.saveAll(executions);
    // Flush the JPA executions + tenant before the raw-JDBC finding/link writes, whose foreign keys
    // (tenant_id, execution_id) would otherwise not see them in this transaction.
    entityManager.flush();
    findingWriter.insertFindings(findingRows, version);
    findingWriter.insertLinks(links);
    return simulationId;
  }

  /**
   * Seed a deep, mostly linear targeted intrusion that reads left to right: the attacker pivots
   * host to host across the estate (workstation, app server, file server, database, jump host, an
   * admin's workstation, then the domain controller) and finally detonates ransomware on a few
   * critical servers. Each hop lands on a NEW host and consumes the finding the previous host
   * yielded, so the graph lays out as one long horizontal kill chain rather than a wide fan-out.
   * Exercises all six finding types.
   */
  public String seedDeepIntrusion(String tenantId, int hops) {
    Tenant tenant = tenantRepository.getReferenceById(tenantId);
    Workflow workflow = demoSimulationWorkflow(tenant, "Attack path demo - targeted intrusion");
    String simulationId = workflow.getSimulation().getId();
    long version = versionService.bump(simulationId, tenantId);
    // `hops` intermediate lateral pivots. A past timeline long enough that even a deep chain keeps
    // every hop strictly ordered and in the past.
    int lateralHops = Math.max(1, hops);
    Instant base = Instant.now().minusSeconds(120L + 60L * (lateralHops + 8L));

    Endpoint ws = endpoint(simulationId + "-ep-ws01", "ws-user-01", "10.0.2.11");
    Endpoint app = endpoint(simulationId + "-ep-appsrv", "app-srv", "10.0.0.5");
    Endpoint file = endpoint(simulationId + "-ep-filesrv", "file-srv", "10.0.0.20");
    Endpoint dc = endpoint(simulationId + "-ep-dc01", "dc01", "10.0.0.10");

    List<AttackPathExecution> executions = new ArrayList<>();
    List<AttackPathFindingWriter.FindingRow> findingRows = new ArrayList<>();
    List<AttackPathFindingWriter.Link> links = new ArrayList<>();

    // Initial access: phishing drops a macro document on the user's workstation.
    Step phishTemplate = new Step();
    phishTemplate.setId(simulationId + "-tmpl-phish");
    Inject phishInject =
        inject(
            simulationId + "-inject-phish",
            "Phishing - macro document",
            tenant,
            simulationId,
            injector("email", "Email", "openbas_email"));
    AttackPathExecution phishing =
        injectorToAssetExecution(
            phishInject, stepInstance(simulationId + "-step-phish", phishTemplate), ws, version);
    phishing.setExecutedAt(base);
    executions.add(phishing);
    addFinding(
        findingRows,
        links,
        phishing,
        simulationId,
        tenantId,
        "file",
        AttackPathSeedFindingValues.file("ws-user-01", "C$", "Users\\Public", "invoice.docm"),
        ws);

    // Discovery: the implant scans and finds RDP open on the app server.
    AttackPathExecution recon =
        pivotHop(
            simulationId,
            tenant,
            workflow,
            version,
            base.plusSeconds(120),
            injector("nmap", "Nmap", "openbas_nmap"),
            "Nmap - network discovery",
            ws,
            app,
            PrimitiveType.FileName,
            ConditionType.EQ,
            "invoice.docm",
            "recon");
    executions.add(recon);
    addFinding(
        findingRows,
        links,
        recon,
        simulationId,
        tenantId,
        "portscan",
        AttackPathSeedFindingValues.portscan("10.0.0.5", 3389, "ms-wbt-server"),
        app);

    // Exploitation: an RDP CVE on the app server.
    AttackPathExecution exploit =
        pivotHop(
            simulationId,
            tenant,
            workflow,
            version,
            base.plusSeconds(240),
            injector("metasploit", "Metasploit", "openbas_metasploit"),
            "RDP exploit (BlueKeep)",
            ws,
            app,
            PrimitiveType.Port,
            ConditionType.EQ,
            "3389",
            "exploit");
    executions.add(exploit);
    addFinding(
        findingRows,
        links,
        exploit,
        simulationId,
        tenantId,
        "cve",
        AttackPathSeedFindingValues.cve("CVE-2019-0708"),
        app);

    // Foothold: dump the first service account on the file server and expose a share.
    String svcBackup = "svc-backup";
    AttackPathExecution toFile =
        pivotHop(
            simulationId,
            tenant,
            workflow,
            version,
            base.plusSeconds(360),
            injector("impacket", "Impacket", "openbas_impacket"),
            "Impacket - lateral movement + secretsdump",
            app,
            file,
            PrimitiveType.CVE,
            ConditionType.EQ,
            "CVE-2019-0708",
            "to-file");
    executions.add(toFile);
    addFinding(
        findingRows,
        links,
        toFile,
        simulationId,
        tenantId,
        "credentials",
        AttackPathSeedFindingValues.credentials(svcBackup, "B@ckup2024"),
        file);
    addFinding(
        findingRows,
        links,
        toFile,
        simulationId,
        tenantId,
        "username",
        AttackPathSeedFindingValues.username("CONTOSO", svcBackup),
        file);
    addFinding(
        findingRows,
        links,
        toFile,
        simulationId,
        tenantId,
        "share",
        AttackPathSeedFindingValues.share("file-srv", "Data", "READ,WRITE"),
        file);

    // Lateral movement across the estate: `hops` host-to-host pivots, each launched with the
    // credential the previous host yielded. Landing on a NEW host each hop is what makes the chain
    // long and horizontal (the graph groups executions by their source host).
    String[] tools = {"crackmapexec", "impacket", "evil-winrm", "psexec", "wmiexec"};
    String[] toolNames = {"CrackMapExec", "Impacket", "Evil-WinRM", "PsExec", "WMIExec"};
    int[] subnetPorts = {445, 3389, 5985};
    String[] subnetServices = {"microsoft-ds", "ms-wbt-server", "wsman"};
    Endpoint prev = file;
    String prevUser = svcBackup;
    for (int i = 0; i < lateralHops; i++) {
      Endpoint next =
          endpoint(
              simulationId + "-ep-host-" + i,
              "host-" + i,
              "10.1." + (i / 250) + "." + (i % 250 + 1));
      String user = "user-" + i;
      AttackPathExecution lateral =
          pivotHop(
              simulationId,
              tenant,
              workflow,
              version,
              base.plusSeconds(420L + i * 30L),
              injector(tools[i % 5], toolNames[i % 5], "openbas_" + tools[i % 5]),
              toolNames[i % 5] + " - lateral movement",
              prev,
              next,
              PrimitiveType.Username,
              ConditionType.EQ,
              prevUser,
              "lat-" + i);
      executions.add(lateral);
      addFinding(
          findingRows,
          links,
          lateral,
          simulationId,
          tenantId,
          "credentials",
          AttackPathSeedFindingValues.credentials(user, "P@ss-" + i),
          next);
      addFinding(
          findingRows,
          links,
          lateral,
          simulationId,
          tenantId,
          "username",
          AttackPathSeedFindingValues.username("CONTOSO", user),
          next);
      // Breadth on this host, collapsing into expandable "+N" clusters in the causal view: the
      // subnet it scanned, the accounts it dumped, and the shares it could read. None of these are
      // consumed by the chain (the next hop keys on `user`), so they add depth of detail without
      // branching the path.
      for (int k = 0; k < 20; k++) {
        addFinding(
            findingRows,
            links,
            lateral,
            simulationId,
            tenantId,
            "portscan",
            AttackPathSeedFindingValues.portscan(
                "10.9." + (i % 250) + "." + (k + 1), subnetPorts[k % 3], subnetServices[k % 3]),
            next);
      }
      for (int k = 0; k < 8; k++) {
        addFinding(
            findingRows,
            links,
            lateral,
            simulationId,
            tenantId,
            "credentials",
            AttackPathSeedFindingValues.credentials("svc-" + i + "-" + k, "Pw-" + i + "-" + k),
            next);
      }
      for (int k = 0; k < 5; k++) {
        addFinding(
            findingRows,
            links,
            lateral,
            simulationId,
            tenantId,
            "share",
            AttackPathSeedFindingValues.share("host-" + i, "Share-" + k, "READ"),
            next);
      }
      prev = next;
      prevUser = user;
    }

    // DCSync from the last compromised host: full domain compromise.
    String domainKey = "krbtgt";
    AttackPathExecution dcSync =
        pivotHop(
            simulationId,
            tenant,
            workflow,
            version,
            base.plusSeconds(420L + lateralHops * 30L),
            injector("mimikatz", "Mimikatz", "openbas_mimikatz"),
            "Mimikatz - DCSync (domain compromise)",
            prev,
            dc,
            PrimitiveType.Username,
            ConditionType.EQ,
            prevUser,
            "dcsync");
    executions.add(dcSync);
    addFinding(
        findingRows,
        links,
        dcSync,
        simulationId,
        tenantId,
        "credentials",
        AttackPathSeedFindingValues.credentials(domainKey, "e3b0c44298fc"),
        dc);

    // Impact: ransomware detonated on a few critical servers from the DC.
    String[] crown = {"srv-finance", "srv-hr", "srv-ops"};
    for (int j = 0; j < crown.length; j++) {
      Endpoint target = endpoint(simulationId + "-ep-crown-" + j, crown[j], "10.0.4." + (10 + j));
      AttackPathExecution impact =
          pivotHop(
              simulationId,
              tenant,
              workflow,
              version,
              base.plusSeconds(450L + lateralHops * 30L),
              injector("ransomware", "Ransomware", "openbas_impacket"),
              "Ransomware deployment",
              dc,
              target,
              PrimitiveType.Username,
              ConditionType.EQ,
              domainKey,
              "impact-" + j);
      executions.add(impact);
    }

    executionRepository.saveAll(executions);
    entityManager.flush();
    findingWriter.insertFindings(findingRows, version);
    findingWriter.insertLinks(links);
    return simulationId;
  }

  /**
   * Fan out {@code footholdCount} independent attack paths converging on one DC: each foothold is
   * compromised (its own unique credential) and then laterally moved to the same DC. Each path keys
   * its consumed condition on its own credential, so the paths stay separate (no cross-match) and
   * the DC renders as the shared chokepoint the whole estate funnels into.
   */
  public String seedScaledChain(String tenantId, int footholdCount) {
    Tenant tenant = tenantRepository.getReferenceById(tenantId);
    Workflow workflow = demoSimulationWorkflow(tenant, "Attack path demo - fan-out to DC");
    String simulationId = workflow.getSimulation().getId();
    long version = versionService.bump(simulationId, tenantId);
    Instant base = Instant.now().minusSeconds(600);

    Endpoint dc = endpoint(simulationId + "-ep-dc01", "dc01", "10.0.0.254");
    // The compromise action shares one condition-less template across footholds; only the lateral
    // hops carry a per-foothold condition, so each path is keyed by its own credential.
    Step compromiseTemplate = new Step();
    compromiseTemplate.setId(simulationId + "-tmpl-compromise");

    List<AttackPathExecution> executions = new ArrayList<>();
    List<AttackPathFindingWriter.FindingRow> findingRows = new ArrayList<>();
    List<AttackPathFindingWriter.Link> links = new ArrayList<>();

    for (int i = 0; i < footholdCount; i++) {
      Endpoint foothold =
          endpoint(simulationId + "-ep-foothold-" + i, "workstation-" + i, "10.0.1." + (10 + i));
      String user = "admin-" + i;

      // Compromise: an injector run that lands on the foothold and dumps its local credential.
      Inject compromiseInject =
          inject(
              simulationId + "-inject-compromise-" + i,
              "CrackMapExec - credential dump",
              tenant,
              simulationId,
              injector("crackmapexec", "CrackMapExec", "openbas_crackmapexec"));
      AttackPathExecution compromise =
          injectorToAssetExecution(
              compromiseInject,
              stepInstance(simulationId + "-step-compromise-" + i, compromiseTemplate),
              foothold,
              version);
      compromise.setExecutedAt(base);
      executions.add(compromise);
      addFinding(
          findingRows,
          links,
          compromise,
          simulationId,
          tenantId,
          "credentials",
          AttackPathSeedFindingValues.credentials(user, "P@ssw0rd-" + i),
          foothold);

      // Lateral: pivot from the foothold to the shared DC, gated on THIS foothold's credential, so
      // every path funnels into the one DC chokepoint.
      AttackPathExecution lateral =
          pivotHop(
              simulationId,
              tenant,
              workflow,
              version,
              base.plusSeconds(60),
              injector("impacket", "Impacket", "openbas_impacket"),
              "Impacket - psexec (lateral to DC)",
              foothold,
              dc,
              PrimitiveType.Username,
              ConditionType.EQ,
              user,
              "lateral-" + i);
      executions.add(lateral);
    }

    executionRepository.saveAll(executions);
    entityManager.flush();
    findingWriter.insertFindings(findingRows, version);
    findingWriter.insertLinks(links);
    return simulationId;
  }

  /**
   * Seed a deep AND wide kill chain (the "ransomware" scenario). An initial-access spine (phishing,
   * discovery, SMB exploit, credential dump) lands a reused local admin on the file server. From
   * there two things happen: the local admin is sprayed across a workstation tier, each host
   * dumping its own domain user and then moving laterally to the domain controller; and, in
   * parallel, a readable share leaks a service account that Kerberoasting cracks and a DCSync turns
   * into the domain key. Every path converges on the one DC, which then fans out a final time as
   * domain-wide ransomware. Each hop consumes the finding the previous hop produced, and the tiers
   * key their conditions on distinct credentials, so the DC renders as the chokepoint the whole
   * estate funnels through. {@code spread} sizes both the workstation tier and the impacted fleet.
   */
  public String seedRansomwareChain(String tenantId, int spread) {
    Tenant tenant = tenantRepository.getReferenceById(tenantId);
    Workflow workflow = demoSimulationWorkflow(tenant, "Attack path demo - ransomware intrusion");
    String simulationId = workflow.getSimulation().getId();
    // Write every stage in the caller's one transaction: the whole chain lands at once, through the
    // same per-stage writer the live replay commits one stage at a time.
    for (int stage = 0; stage < RANSOMWARE_STAGES; stage++) {
      writeRansomwareStage(simulationId, tenantId, tenant, workflow, spread, stage);
    }
    return simulationId;
  }

  /**
   * Create the ransomware demo's simulation (a real Exercise plus one workflow to host its
   * conditioned templates) with no graph yet, and return its id. The live replay then lands the
   * kill chain onto it one stage at a time, so an open Attack path tab shows the intrusion build
   * up.
   */
  public String createRansomwareSimulation(String tenantId) {
    Tenant tenant = tenantRepository.getReferenceById(tenantId);
    Workflow workflow = demoSimulationWorkflow(tenant, "Attack path demo - ransomware (live)");
    return workflow.getSimulation().getId();
  }

  /**
   * Play the next un-played stage of the ransomware kill chain onto an existing simulation, in the
   * caller's transaction. The next stage is the simulation's current version (each stage bumps it
   * once), so a client drives the replay by calling this repeatedly, no state to track.
   */
  public AttackPathReplayStepDTO replayRansomwareNextStage(
      String simulationId, String tenantId, int spread) {
    int stage =
        versionService.current(simulationId, List.of(tenantId)).map(Long::intValue).orElse(0);
    if (stage >= RANSOMWARE_STAGES) {
      return new AttackPathReplayStepDTO(
          RANSOMWARE_STAGES, RANSOMWARE_STAGES, true, "Replay complete");
    }
    Tenant tenant = tenantRepository.getReferenceById(tenantId);
    Workflow workflow =
        workflowRepository.findAllBySimulation_Id(simulationId).stream()
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "No workflow to replay for simulation " + simulationId));
    String label = writeRansomwareStage(simulationId, tenantId, tenant, workflow, spread, stage);
    return new AttackPathReplayStepDTO(
        stage, RANSOMWARE_STAGES, stage + 1 >= RANSOMWARE_STAGES, label);
  }

  /**
   * Build and commit ONE stage of the ransomware kill chain (its conditioned templates, executions
   * and findings) in the caller's transaction, then bump the version and nudge open views. Each
   * stage consumes findings earlier stages already committed, so it renders as a causal step. The
   * stages in order: phishing, discovery, exploitation, credential dump, lateral spray, convergence
   * on the DC, privilege escalation (Kerberoast + DCSync), domain-wide impact.
   */
  private String writeRansomwareStage(
      String simulationId,
      String tenantId,
      Tenant tenant,
      Workflow workflow,
      int spread,
      int stage) {
    long version = versionService.bump(simulationId, tenantId);
    // Past, per-stage timeline: a later stage is written later so its base is greater, and the
    // offsets keep a producer before the consumer that reads it, within and across stages.
    Instant base = Instant.now().minusSeconds(900);
    Endpoint entry = endpoint(simulationId + "-ep-ws01", "ws-user-01", "10.0.2.11");
    Endpoint fileServer = endpoint(simulationId + "-ep-filesrv", "file-srv", "10.0.0.20");
    Endpoint dc = endpoint(simulationId + "-ep-dc01", "dc01", "10.0.0.10");
    String localAdmin = "localadmin";
    String serviceAccount = "svc_sql";
    String domainKey = "krbtgt";

    List<AttackPathExecution> executions = new ArrayList<>();
    List<AttackPathFindingWriter.FindingRow> findingRows = new ArrayList<>();
    List<AttackPathFindingWriter.Link> links = new ArrayList<>();
    String label;

    switch (stage) {
      case 0 -> {
        label = "Initial access - phishing";
        // Phishing drops an implant on the entry workstation (no condition, the chain starts here),
        // producing the file the discovery hop waits on.
        Step phishTemplate = new Step();
        phishTemplate.setId(simulationId + "-tmpl-phish");
        Inject phishInject =
            inject(
                simulationId + "-inject-phish",
                "Phishing - malicious attachment",
                tenant,
                simulationId,
                injector("email", "Email", "openbas_email"));
        AttackPathExecution phishing =
            injectorToAssetExecution(
                phishInject,
                stepInstance(simulationId + "-step-phish", phishTemplate),
                entry,
                version);
        phishing.setExecutedAt(base);
        executions.add(phishing);
        addFinding(
            findingRows,
            links,
            phishing,
            simulationId,
            tenantId,
            "file",
            AttackPathSeedFindingValues.file("ws-user-01", "C$", "Users\\Public", "invoice.exe"),
            entry);
      }
      case 1 -> {
        label = "Discovery - subnet portscan";
        AttackPathExecution discovery =
            pivotHop(
                simulationId,
                tenant,
                workflow,
                version,
                base.plusSeconds(60),
                injector("nmap", "Nmap", "openbas_nmap"),
                "Nmap - subnet discovery",
                entry,
                fileServer,
                PrimitiveType.FileName,
                ConditionType.EQ,
                "invoice.exe",
                "discovery");
        executions.add(discovery);
        addFinding(
            findingRows,
            links,
            discovery,
            simulationId,
            tenantId,
            "portscan",
            AttackPathSeedFindingValues.portscan("10.0.0.20", 445, "microsoft-ds"),
            fileServer);
      }
      case 2 -> {
        label = "Exploitation - SMB CVE";
        AttackPathExecution exploit =
            pivotHop(
                simulationId,
                tenant,
                workflow,
                version,
                base.plusSeconds(120),
                injector("netexec", "NetExec", "openbas_netexec"),
                "NetExec - SMB exploitation",
                entry,
                fileServer,
                PrimitiveType.Port,
                ConditionType.EQ,
                "445",
                "exploit");
        executions.add(exploit);
        addFinding(
            findingRows,
            links,
            exploit,
            simulationId,
            tenantId,
            "cve",
            AttackPathSeedFindingValues.cve("CVE-2017-0144"),
            fileServer);
      }
      case 3 -> {
        label = "Credential access - LSASS dump";
        AttackPathExecution credDump =
            pivotHop(
                simulationId,
                tenant,
                workflow,
                version,
                base.plusSeconds(180),
                injector("impacket", "Impacket", "openbas_impacket"),
                "Impacket - secretsdump (LSASS)",
                fileServer,
                fileServer,
                PrimitiveType.CVE,
                ConditionType.EQ,
                "CVE-2017-0144",
                "creddump");
        executions.add(credDump);
        addFinding(
            findingRows,
            links,
            credDump,
            simulationId,
            tenantId,
            "credentials",
            AttackPathSeedFindingValues.credentials(localAdmin, "aad3b435b51404ee"),
            fileServer);
      }
      case 4 -> {
        label = "Lateral movement - pass-the-hash spray";
        // The reused local admin is sprayed across the workstation tier, each host dumping its own
        // domain user.
        for (int i = 0; i < spread; i++) {
          Endpoint workstation =
              endpoint(simulationId + "-ep-ws-" + i, "workstation-" + i, "10.0.2." + (20 + i));
          AttackPathExecution spray =
              pivotHop(
                  simulationId,
                  tenant,
                  workflow,
                  version,
                  base.plusSeconds(240),
                  injector("crackmapexec", "CrackMapExec", "openbas_crackmapexec"),
                  "CrackMapExec - pass-the-hash",
                  fileServer,
                  workstation,
                  PrimitiveType.Username,
                  ConditionType.EQ,
                  localAdmin,
                  "spray-" + i);
          executions.add(spray);
          addFinding(
              findingRows,
              links,
              spray,
              simulationId,
              tenantId,
              "credentials",
              AttackPathSeedFindingValues.credentials("user-" + i, "P@ssw0rd-" + i),
              workstation);
        }
      }
      case 5 -> {
        label = "Convergence - lateral movement to the DC";
        // Each compromised workstation moves laterally to the shared DC on its own credential.
        for (int i = 0; i < spread; i++) {
          Endpoint workstation =
              endpoint(simulationId + "-ep-ws-" + i, "workstation-" + i, "10.0.2." + (20 + i));
          AttackPathExecution converge =
              pivotHop(
                  simulationId,
                  tenant,
                  workflow,
                  version,
                  base.plusSeconds(300),
                  injector("impacket", "Impacket", "openbas_impacket"),
                  "Impacket - psexec (lateral to DC)",
                  workstation,
                  dc,
                  PrimitiveType.Username,
                  ConditionType.EQ,
                  "user-" + i,
                  "converge-" + i);
          executions.add(converge);
        }
      }
      case 6 -> {
        label = "Privilege escalation - Kerberoast and DCSync";
        // From the file-server foothold a readable share leaks a service account, Kerberoasting
        // cracks it, and a DCSync against the DC yields the domain key.
        AttackPathExecution shareEnum =
            pivotHop(
                simulationId,
                tenant,
                workflow,
                version,
                base.plusSeconds(360),
                injector("crackmapexec", "CrackMapExec", "openbas_crackmapexec"),
                "CrackMapExec - share enumeration",
                fileServer,
                fileServer,
                PrimitiveType.Username,
                ConditionType.EQ,
                localAdmin,
                "shareenum");
        executions.add(shareEnum);
        addFinding(
            findingRows,
            links,
            shareEnum,
            simulationId,
            tenantId,
            "share",
            AttackPathSeedFindingValues.share("file-srv", "SYSVOL", "READ"),
            fileServer);
        addFinding(
            findingRows,
            links,
            shareEnum,
            simulationId,
            tenantId,
            "file",
            AttackPathSeedFindingValues.file("file-srv", "SYSVOL", "policies", "Groups.xml"),
            fileServer);
        AttackPathExecution kerberoast =
            pivotHop(
                simulationId,
                tenant,
                workflow,
                version,
                base.plusSeconds(370),
                injector("impacket", "Impacket", "openbas_impacket"),
                "Impacket - GetUserSPNs (Kerberoast)",
                fileServer,
                fileServer,
                PrimitiveType.ShareName,
                ConditionType.EQ,
                "SYSVOL",
                "kerberoast");
        executions.add(kerberoast);
        addFinding(
            findingRows,
            links,
            kerberoast,
            simulationId,
            tenantId,
            "credentials",
            AttackPathSeedFindingValues.credentials(serviceAccount, "Summer2024!"),
            fileServer);
        addFinding(
            findingRows,
            links,
            kerberoast,
            simulationId,
            tenantId,
            "username",
            AttackPathSeedFindingValues.username("CONTOSO", serviceAccount),
            fileServer);
        AttackPathExecution dcSync =
            pivotHop(
                simulationId,
                tenant,
                workflow,
                version,
                base.plusSeconds(380),
                injector("impacket", "Impacket", "openbas_impacket"),
                "Impacket - secretsdump (DCSync)",
                fileServer,
                dc,
                PrimitiveType.Username,
                ConditionType.EQ,
                serviceAccount,
                "dcsync");
        executions.add(dcSync);
        addFinding(
            findingRows,
            links,
            dcSync,
            simulationId,
            tenantId,
            "credentials",
            AttackPathSeedFindingValues.credentials(domainKey, "e3b0c44298fc1c14"),
            dc);
      }
      case 7 -> {
        label = "Impact - domain-wide ransomware";
        // Ransomware pushed from the DC to the whole fleet.
        for (int j = 0; j < spread; j++) {
          Endpoint fleetHost =
              endpoint(simulationId + "-ep-fleet-" + j, "fleet-" + j, "10.0.3." + (20 + j));
          AttackPathExecution impact =
              pivotHop(
                  simulationId,
                  tenant,
                  workflow,
                  version,
                  base.plusSeconds(420),
                  injector("impacket", "Impacket", "openbas_impacket"),
                  "Impacket - psexec (ransomware deployment)",
                  dc,
                  fleetHost,
                  PrimitiveType.Username,
                  ConditionType.EQ,
                  domainKey,
                  "impact-" + j);
          executions.add(impact);
        }
      }
      default -> throw new IllegalArgumentException("Unknown ransomware stage: " + stage);
    }

    executionRepository.saveAll(executions);
    entityManager.flush();
    findingWriter.insertFindings(findingRows, version);
    findingWriter.insertLinks(links);
    versionService.publishChanged(simulationId, tenantId, version);
    return label;
  }

  /**
   * One pivot hop: an agent action launched from {@code source} against {@code target}, whose
   * conditioned template consumes {@code (keyType op value)} so buildGraph resolves the finding the
   * previous hop produced as its matched producer. {@code label} keys the hop's deterministic ids.
   */
  private AttackPathExecution pivotHop(
      String simulationId,
      Tenant tenant,
      Workflow workflow,
      long version,
      Instant executedAt,
      Injector injector,
      String actionTitle,
      Endpoint source,
      Endpoint target,
      PrimitiveType keyType,
      ConditionType operator,
      String value,
      String label) {
    Step template = conditionedTemplateIn(workflow, keyType, operator, value);
    Inject inject =
        inject(simulationId + "-inject-" + label, actionTitle, tenant, simulationId, injector);
    Agent agent = agentOn(source, simulationId + "-agent-" + label);
    AttackPathExecution execution =
        agentPivotExecution(
            inject,
            stepInstance(simulationId + "-step-" + label, template),
            agent,
            source,
            target,
            version);
    execution.setExecutedAt(executedAt);
    return execution;
  }

  /** Records a finding produced on {@code endpoint} and links it to its producing execution. */
  private void addFinding(
      List<AttackPathFindingWriter.FindingRow> rows,
      List<AttackPathFindingWriter.Link> links,
      AttackPathExecution producer,
      String simulationId,
      String tenantId,
      String type,
      String value,
      Endpoint endpoint) {
    String id = AttackPathIds.findingRow(simulationId, type, type, value, endpoint.getId());
    rows.add(
        new AttackPathFindingWriter.FindingRow(
            id,
            tenantId,
            simulationId,
            type,
            type,
            value,
            endpoint.getId(),
            null,
            endpoint.getId(),
            true));
    links.add(new AttackPathFindingWriter.Link(producer.getId(), id));
  }

  /**
   * The injector -&gt; asset execution recipe, byte-identical to {@code
   * AttackPathExecutionIngestionService.setSourceInjectorTargetAsset}: the deterministic {@code
   * executionNode} id, then the engine setters that freeze the identity/source/target columns. The
   * domain objects are transient (the execution freezes their values into plain columns; only
   * {@code tenant} is a real FK), so nothing but the execution row is persisted.
   */
  private AttackPathExecution injectorToAssetExecution(
      Inject inject, Step step, Endpoint endpoint, long rowVersion) {
    AttackPathExecution execution = new AttackPathExecution();
    execution.setId(
        AttackPathIds.executionNode(
            inject.getId(), endpoint.getId(), inject.getInjector().getId()));
    execution.setGlobalInformation(step, inject);
    execution.setSourceInjectorInformation(inject.getInjector());
    execution.setTargetAssetInformation(endpoint);
    execution.setRowVersion(rowVersion);
    return execution;
  }

  /**
   * The agent -&gt; asset (pivot) execution recipe, byte-identical to the agent branch of {@code
   * AttackPathExecutionIngestionService}: the source is an agent on {@code sourceEndpoint}, so that
   * endpoint renders as both a reached target and a pivot source in the graph.
   */
  private AttackPathExecution agentPivotExecution(
      Inject inject,
      Step step,
      Agent agent,
      Endpoint sourceEndpoint,
      Endpoint targetEndpoint,
      long rowVersion) {
    AttackPathExecution execution = new AttackPathExecution();
    execution.setId(
        AttackPathIds.executionNode(inject.getId(), targetEndpoint.getId(), agent.getId()));
    execution.setGlobalInformation(step, inject);
    execution.setSourceAgentInformation(agent, sourceEndpoint);
    execution.setTargetAssetInformation(targetEndpoint);
    execution.setRowVersion(rowVersion);
    return execution;
  }

  /**
   * A persisted step template (in a workflow of a simulation) carrying one filter condition, so
   * buildGraph resolves it as a consumed key on the executions that ran it. The workflow needs a
   * simulation (or scenario) per chk_workflow_simulation_or_scenario; the executions carry the
   * simulation id as a frozen string, so the two are decoupled.
   */
  private Step conditionedTemplate(
      Tenant tenant, String label, PrimitiveType keyType, ConditionType operator, String value) {
    Exercise simulation = new Exercise();
    simulation.setName("Causal seed " + label);
    simulation.setFrom("causal-seed@openaev.io");
    simulation.setTenant(tenant);
    exerciseRepository.save(simulation);
    Workflow workflow = workflowTemplate();
    workflow.setSimulation(simulation);
    workflowRepository.save(workflow);
    Step template = stepTemplate(workflow);
    stepRepository.save(template);
    Condition condition = new Condition();
    condition.setKeyTypes(List.of(keyType));
    condition.setType(operator);
    condition.setValue(value);
    conditionService.linkToStep(condition, template, true);
    conditionRepository.save(condition);
    return template;
  }

  /**
   * Create the demo simulation the front navigates to (a real Exercise) plus one workflow to host
   * all of its conditioned step templates, and return the workflow. The attack-path rows are
   * written under {@code workflow.getSimulation().getId()} so that simulation's Attack path tab
   * renders them.
   */
  private Workflow demoSimulationWorkflow(Tenant tenant, String name) {
    Exercise simulation = new Exercise();
    simulation.setName(name);
    simulation.setFrom("causal-seed@openaev.io");
    simulation.setTenant(tenant);
    exerciseRepository.save(simulation);
    Workflow workflow = workflowTemplate();
    workflow.setSimulation(simulation);
    workflowRepository.save(workflow);
    return workflow;
  }

  /** A conditioned step template hosted in the given (shared) workflow. */
  private Step conditionedTemplateIn(
      Workflow workflow, PrimitiveType keyType, ConditionType operator, String value) {
    Step template = stepTemplate(workflow);
    stepRepository.save(template);
    Condition condition = new Condition();
    condition.setKeyTypes(List.of(keyType));
    condition.setType(operator);
    condition.setValue(value);
    conditionService.linkToStep(condition, template, true);
    conditionRepository.save(condition);
    return template;
  }

  private Endpoint endpoint(String id, String name, String ip) {
    Endpoint endpoint = new Endpoint();
    endpoint.setId(id);
    endpoint.setName(name);
    endpoint.setHostname(name);
    endpoint.setIps(new String[] {ip});
    endpoint.setPlatform(Endpoint.PLATFORM_TYPE.Windows);
    return endpoint;
  }

  private Injector injector(String id, String name, String type) {
    Injector injector = new Injector();
    injector.setId(id);
    injector.setName(name);
    injector.setType(type);
    return injector;
  }

  private Inject inject(
      String id, String title, Tenant tenant, String simulationId, Injector injector) {
    Exercise simulation = new Exercise();
    simulation.setId(simulationId);
    Inject inject = new Inject();
    inject.setId(id);
    inject.setTitle(title);
    inject.setTenant(tenant);
    inject.setExercise(simulation);
    inject.setInjector(injector);
    return inject;
  }

  private Agent agentOn(Endpoint endpoint, String id) {
    Agent agent = new Agent();
    agent.setId(id);
    agent.setAsset(endpoint);
    agent.setPrivilege(Agent.PRIVILEGE.admin);
    return agent;
  }

  private static Step stepInstance(String id, Step template) {
    Step step = new Step();
    step.setId(id);
    step.setStepTemplate(template);
    return step;
  }

  private static Workflow workflowTemplate() {
    Workflow workflow = new Workflow();
    workflow.setStatus(WorkflowStatus.TEMPLATE);
    workflow.setVersion(1);
    workflow.setEdited(false);
    workflow.setWorkflowCreatedAt(Instant.now());
    workflow.setWorkflowUpdatedAt(Instant.now());
    workflow.setWorkflowTemplate(null);
    workflow.setWorkflowsExecuted(new ArrayList<>());
    workflow.setSteps(new ArrayList<>());
    return workflow;
  }

  private static Step stepTemplate(Workflow workflow) {
    Step step = new Step();
    step.setWorkflow(workflow);
    step.setStepAction(StepActionClass.INJECT_EXECUTION);
    step.setOutput("{}");
    step.setOutputParser("{}");
    step.setInput("{}");
    step.setData("{}");
    step.setLimitExecution(1);
    step.setConditionExecuted("true");
    step.setStatus(StepStatus.TEMPLATE);
    return step;
  }
}
