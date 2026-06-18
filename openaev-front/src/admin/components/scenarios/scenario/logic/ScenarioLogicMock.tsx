/**
 * ScenarioLogicMock — static event-action chain logic map for mock chaining scenarios.
 * Uses @xyflow/react to render a read-only ReactFlow graph matching each scenario's attack chain.
 */
import { BoltOutlined, PlayArrowOutlined } from '@mui/icons-material';
import { Chip, Typography } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import {
  type Edge,
  type Node,
  ReactFlow,
  Background,
  BackgroundVariant,
  Controls,
  MiniMap,
  MarkerType,
  Position,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';
import { type FunctionComponent } from 'react';
import { useParams } from 'react-router';

// ── Node & Edge data ─────────────────────────────────────────────────────────

interface EventData { label: string; description: string; types: string[] }
interface ActionData { label: string; injectorType: string; attackPattern: string }

type MockNode = Node<EventData | ActionData>;

// Finance 5EP — Credential Theft Chain
const FINANCE_NODES: MockNode[] = [
  { id: 'e1', type: 'input', position: { x: 80, y: 60 },
    data: { label: 'Phishing Email Received', description: 'Trigger: inbound email with malicious attachment', types: ['email_received'] } as EventData,
    style: { background: 'rgba(255,152,0,0.12)', border: '1.5px solid rgba(255,152,0,0.5)', borderRadius: 24, padding: '10px 18px', color: '#fff', minWidth: 220 },
    sourcePosition: Position.Right, targetPosition: Position.Left },
  { id: 'a1', position: { x: 380, y: 50 },
    data: { label: 'Execute Phishing Payload', injectorType: 'openaev_windows_powershell', attackPattern: 'T1566.001' } as ActionData,
    style: { background: 'rgba(33,150,243,0.12)', border: '1.5px solid rgba(33,150,243,0.5)', borderRadius: 8, padding: '10px 18px', color: '#fff', minWidth: 200 },
    sourcePosition: Position.Right, targetPosition: Position.Left },
  { id: 'e2', position: { x: 680, y: 50 },
    data: { label: 'Initial Access Confirmed', description: 'Finding: FINANCE-WS-01 shell active', types: ['finding'] } as EventData,
    style: { background: 'rgba(255,152,0,0.12)', border: '1.5px solid rgba(255,152,0,0.5)', borderRadius: 24, padding: '10px 18px', color: '#fff', minWidth: 220 },
    sourcePosition: Position.Right, targetPosition: Position.Left },
  { id: 'a2', position: { x: 980, y: 20 },
    data: { label: 'Dump LSASS (Mimikatz)', injectorType: 'openaev_windows_powershell', attackPattern: 'T1003.001' } as ActionData,
    style: { background: 'rgba(33,150,243,0.12)', border: '1.5px solid rgba(33,150,243,0.5)', borderRadius: 8, padding: '10px 18px', color: '#fff', minWidth: 200 },
    sourcePosition: Position.Right, targetPosition: Position.Left },
  { id: 'a3', position: { x: 980, y: 100 },
    data: { label: 'Kerberoast svc_sqlreport', injectorType: 'openaev_windows_powershell', attackPattern: 'T1558.003' } as ActionData,
    style: { background: 'rgba(33,150,243,0.12)', border: '1.5px solid rgba(33,150,243,0.5)', borderRadius: 8, padding: '10px 18px', color: '#fff', minWidth: 200 },
    sourcePosition: Position.Right, targetPosition: Position.Left },
  { id: 'e3', position: { x: 1280, y: 50 },
    data: { label: 'Credentials Found', description: 'Finding: domain credentials captured', types: ['finding'] } as EventData,
    style: { background: 'rgba(255,152,0,0.12)', border: '1.5px solid rgba(255,152,0,0.5)', borderRadius: 24, padding: '10px 18px', color: '#fff', minWidth: 200 },
    sourcePosition: Position.Right, targetPosition: Position.Left },
  { id: 'a4', position: { x: 1560, y: 20 },
    data: { label: 'Lateral Move PtH → FILE-SRV-01', injectorType: 'openaev_windows_powershell', attackPattern: 'T1021.002' } as ActionData,
    style: { background: 'rgba(33,150,243,0.12)', border: '1.5px solid rgba(33,150,243,0.5)', borderRadius: 8, padding: '10px 18px', color: '#fff', minWidth: 230 },
    sourcePosition: Position.Right, targetPosition: Position.Left },
  { id: 'e4', position: { x: 1860, y: 40 },
    data: { label: 'File Server Accessed', description: 'Finding: FILE-SRV-01 admin share accessible', types: ['finding'] } as EventData,
    style: { background: 'rgba(255,152,0,0.12)', border: '1.5px solid rgba(255,152,0,0.5)', borderRadius: 24, padding: '10px 18px', color: '#fff', minWidth: 200 },
    sourcePosition: Position.Right, targetPosition: Position.Left },
  { id: 'a5', type: 'output', position: { x: 2140, y: 40 },
    data: { label: 'Exfiltrate Financial Reports', injectorType: 'openaev_network', attackPattern: 'T1041' } as ActionData,
    style: { background: 'rgba(33,150,243,0.12)', border: '1.5px solid rgba(33,150,243,0.5)', borderRadius: 8, padding: '10px 18px', color: '#fff', minWidth: 200 },
    sourcePosition: Position.Right, targetPosition: Position.Left },
];

const FINANCE_EDGES: Edge[] = [
  { id: 'e1-a1', source: 'e1', target: 'a1', markerEnd: { type: MarkerType.Arrow }, style: { stroke: '#ff9800' } },
  { id: 'a1-e2', source: 'a1', target: 'e2', markerEnd: { type: MarkerType.Arrow }, style: { stroke: '#2196f3', strokeDasharray: '5 3' } },
  { id: 'e2-a2', source: 'e2', target: 'a2', markerEnd: { type: MarkerType.Arrow }, style: { stroke: '#ff9800' } },
  { id: 'e2-a3', source: 'e2', target: 'a3', markerEnd: { type: MarkerType.Arrow }, style: { stroke: '#ff9800' } },
  { id: 'a2-e3', source: 'a2', target: 'e3', markerEnd: { type: MarkerType.Arrow }, style: { stroke: '#2196f3', strokeDasharray: '5 3' } },
  { id: 'a3-e3', source: 'a3', target: 'e3', markerEnd: { type: MarkerType.Arrow }, style: { stroke: '#2196f3', strokeDasharray: '5 3' } },
  { id: 'e3-a4', source: 'e3', target: 'a4', markerEnd: { type: MarkerType.Arrow }, style: { stroke: '#ff9800' } },
  { id: 'a4-e4', source: 'a4', target: 'e4', markerEnd: { type: MarkerType.Arrow }, style: { stroke: '#2196f3', strokeDasharray: '5 3' } },
  { id: 'e4-a5', source: 'e4', target: 'a5', markerEnd: { type: MarkerType.Arrow }, style: { stroke: '#ff9800' } },
];

// APT 15EP — Multi-stage Campaign
const APT_NODES: MockNode[] = [
  { id: 'e1', type: 'input', position: { x: 60, y: 200 },
    data: { label: 'Web Service Exposure', description: 'Trigger: public web server reachable on 443/tcp', types: ['network_exposure'] } as EventData,
    style: { background: 'rgba(255,152,0,0.12)', border: '1.5px solid rgba(255,152,0,0.5)', borderRadius: 24, padding: '10px 18px', color: '#fff', minWidth: 220 } },
  { id: 'a1', position: { x: 360, y: 190 },
    data: { label: 'Exploit CVE-2023-44487 (HTTP/2 Reset)', injectorType: 'openaev_http', attackPattern: 'T1190' } as ActionData,
    style: { background: 'rgba(33,150,243,0.12)', border: '1.5px solid rgba(33,150,243,0.5)', borderRadius: 8, padding: '10px 18px', color: '#fff', minWidth: 220 } },
  { id: 'e2', position: { x: 660, y: 190 },
    data: { label: 'RCE Confirmed on WEB-DMZ-01', description: 'Finding: webshell active', types: ['finding'] } as EventData,
    style: { background: 'rgba(255,152,0,0.12)', border: '1.5px solid rgba(255,152,0,0.5)', borderRadius: 24, padding: '10px 18px', color: '#fff', minWidth: 220 } },
  { id: 'a2', position: { x: 960, y: 120 },
    data: { label: 'Pivot to Internal — SSH Tunnel', injectorType: 'openaev_network', attackPattern: 'T1572' } as ActionData,
    style: { background: 'rgba(33,150,243,0.12)', border: '1.5px solid rgba(33,150,243,0.5)', borderRadius: 8, padding: '10px 18px', color: '#fff', minWidth: 210 } },
  { id: 'a3', position: { x: 960, y: 250 },
    data: { label: 'Deploy Persistent Backdoor (cron)', injectorType: 'openaev_linux_command', attackPattern: 'T1053.003' } as ActionData,
    style: { background: 'rgba(33,150,243,0.12)', border: '1.5px solid rgba(33,150,243,0.5)', borderRadius: 8, padding: '10px 18px', color: '#fff', minWidth: 210 } },
  { id: 'e3', position: { x: 1260, y: 110 },
    data: { label: 'Corp LAN Reachable', description: 'Trigger: tunnel active, LAN accessible', types: ['network_reachable'] } as EventData,
    style: { background: 'rgba(255,152,0,0.12)', border: '1.5px solid rgba(255,152,0,0.5)', borderRadius: 24, padding: '10px 18px', color: '#fff', minWidth: 220 } },
  { id: 'a4', position: { x: 1560, y: 40 },
    data: { label: 'Exploit ProxyShell on Exchange', injectorType: 'openaev_http', attackPattern: 'T1190' } as ActionData,
    style: { background: 'rgba(33,150,243,0.12)', border: '1.5px solid rgba(33,150,243,0.5)', borderRadius: 8, padding: '10px 18px', color: '#fff', minWidth: 220 } },
  { id: 'a5', position: { x: 1560, y: 160 },
    data: { label: 'LLMNR Poison + NTLMv2 Capture', injectorType: 'openaev_network', attackPattern: 'T1557.001' } as ActionData,
    style: { background: 'rgba(33,150,243,0.12)', border: '1.5px solid rgba(33,150,243,0.5)', borderRadius: 8, padding: '10px 18px', color: '#fff', minWidth: 230 } },
  { id: 'e4', position: { x: 1860, y: 100 },
    data: { label: 'Domain Creds Captured', description: 'Finding: NTLMv2 hash cracked or DA token impersonated', types: ['finding'] } as EventData,
    style: { background: 'rgba(255,152,0,0.12)', border: '1.5px solid rgba(255,152,0,0.5)', borderRadius: 24, padding: '10px 18px', color: '#fff', minWidth: 220 } },
  { id: 'a6', position: { x: 2160, y: 60 },
    data: { label: 'DCSync — Dump All Hashes', injectorType: 'openaev_windows_powershell', attackPattern: 'T1003.006' } as ActionData,
    style: { background: 'rgba(33,150,243,0.12)', border: '1.5px solid rgba(33,150,243,0.5)', borderRadius: 8, padding: '10px 18px', color: '#fff', minWidth: 210 } },
  { id: 'a7', type: 'output', position: { x: 2160, y: 180 },
    data: { label: 'Exfiltrate M&A + HR Data', injectorType: 'openaev_network', attackPattern: 'T1041' } as ActionData,
    style: { background: 'rgba(33,150,243,0.12)', border: '1.5px solid rgba(33,150,243,0.5)', borderRadius: 8, padding: '10px 18px', color: '#fff', minWidth: 210 } },
];

const APT_EDGES: Edge[] = [
  { id: 'e1-a1', source: 'e1', target: 'a1', markerEnd: { type: MarkerType.Arrow }, style: { stroke: '#ff9800' } },
  { id: 'a1-e2', source: 'a1', target: 'e2', markerEnd: { type: MarkerType.Arrow }, style: { stroke: '#2196f3', strokeDasharray: '5 3' } },
  { id: 'e2-a2', source: 'e2', target: 'a2', markerEnd: { type: MarkerType.Arrow }, style: { stroke: '#ff9800' } },
  { id: 'e2-a3', source: 'e2', target: 'a3', markerEnd: { type: MarkerType.Arrow }, style: { stroke: '#ff9800' } },
  { id: 'a2-e3', source: 'a2', target: 'e3', markerEnd: { type: MarkerType.Arrow }, style: { stroke: '#2196f3', strokeDasharray: '5 3' } },
  { id: 'e3-a4', source: 'e3', target: 'a4', markerEnd: { type: MarkerType.Arrow }, style: { stroke: '#ff9800' } },
  { id: 'e3-a5', source: 'e3', target: 'a5', markerEnd: { type: MarkerType.Arrow }, style: { stroke: '#ff9800' } },
  { id: 'a4-e4', source: 'a4', target: 'e4', markerEnd: { type: MarkerType.Arrow }, style: { stroke: '#2196f3', strokeDasharray: '5 3' } },
  { id: 'a5-e4', source: 'a5', target: 'e4', markerEnd: { type: MarkerType.Arrow }, style: { stroke: '#2196f3', strokeDasharray: '5 3' } },
  { id: 'e4-a6', source: 'e4', target: 'a6', markerEnd: { type: MarkerType.Arrow }, style: { stroke: '#ff9800' } },
  { id: 'e4-a7', source: 'e4', target: 'a7', markerEnd: { type: MarkerType.Arrow }, style: { stroke: '#ff9800' } },
];

// Enterprise 50EP — Advanced Full Breach
const ENTERPRISE_NODES: MockNode[] = [
  { id: 'e1', type: 'input', position: { x: 60, y: 300 },
    data: { label: 'Public-Facing Service Reachable', description: 'Trigger: Citrix ADC on 443/tcp from internet', types: ['network_exposure'] } as EventData,
    style: { background: 'rgba(255,152,0,0.12)', border: '1.5px solid rgba(255,152,0,0.5)', borderRadius: 24, padding: '10px 18px', color: '#fff', minWidth: 230 } },
  { id: 'a1', position: { x: 380, y: 280 },
    data: { label: 'Exploit Citrix CVE-2023-3519', injectorType: 'openaev_http', attackPattern: 'T1190' } as ActionData,
    style: { background: 'rgba(33,150,243,0.12)', border: '1.5px solid rgba(33,150,243,0.5)', borderRadius: 8, padding: '10px 18px', color: '#fff', minWidth: 210 } },
  { id: 'e2', position: { x: 680, y: 270 },
    data: { label: 'DMZ Foothold Established', description: 'Finding: webshell + reverse shell active', types: ['finding'] } as EventData,
    style: { background: 'rgba(255,152,0,0.12)', border: '1.5px solid rgba(255,152,0,0.5)', borderRadius: 24, padding: '10px 18px', color: '#fff', minWidth: 220 } },
  { id: 'a2', position: { x: 980, y: 140 },
    data: { label: 'Network Recon — AD Enumeration (Bloodhound)', injectorType: 'openaev_windows_powershell', attackPattern: 'T1087.002' } as ActionData,
    style: { background: 'rgba(33,150,243,0.12)', border: '1.5px solid rgba(33,150,243,0.5)', borderRadius: 8, padding: '10px 18px', color: '#fff', minWidth: 260 } },
  { id: 'a3', position: { x: 980, y: 300 },
    data: { label: 'PrintNightmare Privilege Escalation', injectorType: 'openaev_windows_powershell', attackPattern: 'T1068' } as ActionData,
    style: { background: 'rgba(33,150,243,0.12)', border: '1.5px solid rgba(33,150,243,0.5)', borderRadius: 8, padding: '10px 18px', color: '#fff', minWidth: 230 } },
  { id: 'a4', position: { x: 980, y: 460 },
    data: { label: 'Exploit AD CS ESC1 (Certipy)', injectorType: 'openaev_windows_powershell', attackPattern: 'T1649' } as ActionData,
    style: { background: 'rgba(33,150,243,0.12)', border: '1.5px solid rgba(33,150,243,0.5)', borderRadius: 8, padding: '10px 18px', color: '#fff', minWidth: 220 } },
  { id: 'e3', position: { x: 1320, y: 220 },
    data: { label: 'DC-01 Admin Access Achieved', description: 'Finding: SYSTEM on DC-01', types: ['finding'] } as EventData,
    style: { background: 'rgba(255,152,0,0.12)', border: '1.5px solid rgba(255,152,0,0.5)', borderRadius: 24, padding: '10px 18px', color: '#fff', minWidth: 220 } },
  { id: 'a5', position: { x: 1620, y: 120 },
    data: { label: 'DCSync — Dump NTDS.dit + krbtgt', injectorType: 'openaev_windows_powershell', attackPattern: 'T1003.003' } as ActionData,
    style: { background: 'rgba(33,150,243,0.12)', border: '1.5px solid rgba(33,150,243,0.5)', borderRadius: 8, padding: '10px 18px', color: '#fff', minWidth: 230 } },
  { id: 'a6', position: { x: 1620, y: 260 },
    data: { label: 'Forge Golden Ticket — Persist DA', injectorType: 'openaev_windows_powershell', attackPattern: 'T1558.001' } as ActionData,
    style: { background: 'rgba(33,150,243,0.12)', border: '1.5px solid rgba(33,150,243,0.5)', borderRadius: 8, padding: '10px 18px', color: '#fff', minWidth: 230 } },
  { id: 'a7', position: { x: 1620, y: 400 },
    data: { label: 'Pivot to OT Network — Historian', injectorType: 'openaev_network', attackPattern: 'T0817' } as ActionData,
    style: { background: 'rgba(33,150,243,0.12)', border: '1.5px solid rgba(33,150,243,0.5)', borderRadius: 8, padding: '10px 18px', color: '#fff', minWidth: 220 } },
  { id: 'e4', position: { x: 1960, y: 300 },
    data: { label: 'Full Enterprise Breach', description: 'Finding: AD + OT + Cloud access confirmed', types: ['finding'] } as EventData,
    style: { background: 'rgba(244,67,54,0.15)', border: '1.5px solid rgba(244,67,54,0.6)', borderRadius: 24, padding: '10px 18px', color: '#fff', minWidth: 220 } },
  { id: 'a8', type: 'output', position: { x: 2260, y: 220 },
    data: { label: 'Extract Cloud Secrets (Azure Owner)', injectorType: 'openaev_http', attackPattern: 'T1078.004' } as ActionData,
    style: { background: 'rgba(33,150,243,0.12)', border: '1.5px solid rgba(33,150,243,0.5)', borderRadius: 8, padding: '10px 18px', color: '#fff', minWidth: 230 } },
  { id: 'a9', type: 'output', position: { x: 2260, y: 380 },
    data: { label: 'Exfiltrate OT Schematics + PLC Logic', injectorType: 'openaev_network', attackPattern: 'T1041' } as ActionData,
    style: { background: 'rgba(33,150,243,0.12)', border: '1.5px solid rgba(33,150,243,0.5)', borderRadius: 8, padding: '10px 18px', color: '#fff', minWidth: 250 } },
];

const ENTERPRISE_EDGES: Edge[] = [
  { id: 'e1-a1', source: 'e1', target: 'a1', markerEnd: { type: MarkerType.Arrow }, style: { stroke: '#ff9800' } },
  { id: 'a1-e2', source: 'a1', target: 'e2', markerEnd: { type: MarkerType.Arrow }, style: { stroke: '#2196f3', strokeDasharray: '5 3' } },
  { id: 'e2-a2', source: 'e2', target: 'a2', markerEnd: { type: MarkerType.Arrow }, style: { stroke: '#ff9800' } },
  { id: 'e2-a3', source: 'e2', target: 'a3', markerEnd: { type: MarkerType.Arrow }, style: { stroke: '#ff9800' } },
  { id: 'e2-a4', source: 'e2', target: 'a4', markerEnd: { type: MarkerType.Arrow }, style: { stroke: '#ff9800' } },
  { id: 'a2-e3', source: 'a2', target: 'e3', markerEnd: { type: MarkerType.Arrow }, style: { stroke: '#2196f3', strokeDasharray: '5 3' } },
  { id: 'a3-e3', source: 'a3', target: 'e3', markerEnd: { type: MarkerType.Arrow }, style: { stroke: '#2196f3', strokeDasharray: '5 3' } },
  { id: 'a4-e3', source: 'a4', target: 'e3', markerEnd: { type: MarkerType.Arrow }, style: { stroke: '#2196f3', strokeDasharray: '5 3' } },
  { id: 'e3-a5', source: 'e3', target: 'a5', markerEnd: { type: MarkerType.Arrow }, style: { stroke: '#ff9800' } },
  { id: 'e3-a6', source: 'e3', target: 'a6', markerEnd: { type: MarkerType.Arrow }, style: { stroke: '#ff9800' } },
  { id: 'e3-a7', source: 'e3', target: 'a7', markerEnd: { type: MarkerType.Arrow }, style: { stroke: '#ff9800' } },
  { id: 'a5-e4', source: 'a5', target: 'e4', markerEnd: { type: MarkerType.Arrow }, style: { stroke: '#2196f3', strokeDasharray: '5 3' } },
  { id: 'a6-e4', source: 'a6', target: 'e4', markerEnd: { type: MarkerType.Arrow }, style: { stroke: '#2196f3', strokeDasharray: '5 3' } },
  { id: 'a7-e4', source: 'a7', target: 'e4', markerEnd: { type: MarkerType.Arrow }, style: { stroke: '#2196f3', strokeDasharray: '5 3' } },
  { id: 'e4-a8', source: 'e4', target: 'a8', markerEnd: { type: MarkerType.Arrow }, style: { stroke: '#ff9800' } },
  { id: 'e4-a9', source: 'e4', target: 'a9', markerEnd: { type: MarkerType.Arrow }, style: { stroke: '#ff9800' } },
];

// ── APT29 Domain Takeover — Event-Action Chain Logic ─────────────────────────
const APT_DOMAIN_NODES: MockNode[] = [
  // Trigger
  { id: 'e1', type: 'input', position: { x: 60, y: 120 },
    data: { label: 'Schedule: Red Team Kickoff', description: 'Trigger: manual kick-off at 07:00 UTC by red team lead', types: ['manual'] } as EventData,
    style: { background: 'rgba(255,152,0,0.12)', border: '1.5px solid rgba(255,152,0,0.5)', borderRadius: 24, padding: '10px 18px', color: '#fff', minWidth: 220 },
    sourcePosition: Position.Right, targetPosition: Position.Left },
  // Phase 1 – External Recon
  { id: 'a1', position: { x: 360, y: 80 },
    data: { label: 'Nmap External SYN Scan', injectorType: 'openaev_nmap', attackPattern: 'T1046' } as ActionData,
    style: { background: 'rgba(33,150,243,0.12)', border: '1.5px solid rgba(33,150,243,0.5)', borderRadius: 8, padding: '10px 18px', color: '#fff', minWidth: 200 },
    sourcePosition: Position.Right, targetPosition: Position.Left },
  { id: 'e2', position: { x: 650, y: 80 },
    data: { label: 'Tomcat AJP Port Found', description: 'Finding: port 8009/tcp open on WEB-APP-01', types: ['finding'] } as EventData,
    style: { background: 'rgba(255,152,0,0.12)', border: '1.5px solid rgba(255,152,0,0.5)', borderRadius: 24, padding: '10px 18px', color: '#fff', minWidth: 220 },
    sourcePosition: Position.Right, targetPosition: Position.Left },
  { id: 'a2', position: { x: 960, y: 80 },
    data: { label: 'Nuclei CVE-2020-1938 (Ghostcat)', injectorType: 'openaev_nuclei', attackPattern: 'T1190' } as ActionData,
    style: { background: 'rgba(33,150,243,0.12)', border: '1.5px solid rgba(33,150,243,0.5)', borderRadius: 8, padding: '10px 18px', color: '#fff', minWidth: 200 },
    sourcePosition: Position.Right, targetPosition: Position.Left },
  // Phase 2 – Internal Recon
  { id: 'e3', position: { x: 1260, y: 80 },
    data: { label: 'Initial Access via Tomcat RCE', description: 'Finding: www-data shell on WEB-APP-01, svc_tomcat creds extracted', types: ['finding'] } as EventData,
    style: { background: 'rgba(255,152,0,0.12)', border: '1.5px solid rgba(255,152,0,0.5)', borderRadius: 24, padding: '10px 18px', color: '#fff', minWidth: 220 },
    sourcePosition: Position.Right, targetPosition: Position.Left },
  { id: 'a3', position: { x: 1560, y: 40 },
    data: { label: 'Netexec SMB Internal Discovery', injectorType: 'openaev_netexec', attackPattern: 'T1135' } as ActionData,
    style: { background: 'rgba(33,150,243,0.12)', border: '1.5px solid rgba(33,150,243,0.5)', borderRadius: 8, padding: '10px 18px', color: '#fff', minWidth: 200 },
    sourcePosition: Position.Right, targetPosition: Position.Left },
  { id: 'a4', position: { x: 1560, y: 130 },
    data: { label: 'Netexec SMB Credential Spray', injectorType: 'openaev_netexec', attackPattern: 'T1110.003' } as ActionData,
    style: { background: 'rgba(33,150,243,0.12)', border: '1.5px solid rgba(33,150,243,0.5)', borderRadius: 8, padding: '10px 18px', color: '#fff', minWidth: 200 },
    sourcePosition: Position.Right, targetPosition: Position.Left },
  // Phase 3 – Lateral Movement
  { id: 'e4', position: { x: 1860, y: 80 },
    data: { label: 'DEV-WS-01 Compromised', description: 'Finding: SMB admin access as svc_tomcat on DEV-WS-01', types: ['finding'] } as EventData,
    style: { background: 'rgba(255,152,0,0.12)', border: '1.5px solid rgba(255,152,0,0.5)', borderRadius: 24, padding: '10px 18px', color: '#fff', minWidth: 220 },
    sourcePosition: Position.Right, targetPosition: Position.Left },
  { id: 'a5', position: { x: 2160, y: 40 },
    data: { label: 'Netexec WMI LSASS Dump', injectorType: 'openaev_netexec', attackPattern: 'T1003.001' } as ActionData,
    style: { background: 'rgba(244,67,54,0.12)', border: '1.5px solid rgba(244,67,54,0.5)', borderRadius: 8, padding: '10px 18px', color: '#fff', minWidth: 200 },
    sourcePosition: Position.Right, targetPosition: Position.Left },
  // Branch A – PrintNightmare
  { id: 'a6', position: { x: 2460, y: -60 },
    data: { label: 'Netexec PrintNightmare CVE-2021-1675', injectorType: 'openaev_netexec', attackPattern: 'T1068' } as ActionData,
    style: { background: 'rgba(33,150,243,0.12)', border: '1.5px solid rgba(33,150,243,0.5)', borderRadius: 8, padding: '10px 18px', color: '#fff', minWidth: 200 },
    sourcePosition: Position.Right, targetPosition: Position.Left },
  // Branch B – MSSQL (blocked)
  { id: 'a7', position: { x: 2460, y: 40 },
    data: { label: 'Netexec MSSQL SA Brute Force', injectorType: 'openaev_netexec', attackPattern: 'T1110.001' } as ActionData,
    style: { background: 'rgba(244,67,54,0.12)', border: '1.5px solid rgba(244,67,54,0.5)', borderRadius: 8, padding: '10px 18px', color: '#fff', minWidth: 200 },
    sourcePosition: Position.Right, targetPosition: Position.Left },
  // Branch C – IT Admin
  { id: 'a8', position: { x: 2460, y: 150 },
    data: { label: 'Netexec SMB Pass-the-Hash (IT Admin)', injectorType: 'openaev_netexec', attackPattern: 'T1550.002' } as ActionData,
    style: { background: 'rgba(33,150,243,0.12)', border: '1.5px solid rgba(33,150,243,0.5)', borderRadius: 8, padding: '10px 18px', color: '#fff', minWidth: 200 },
    sourcePosition: Position.Right, targetPosition: Position.Left },
  // Phase 4 – Domain Compromise
  { id: 'e5', position: { x: 2760, y: 40 },
    data: { label: 'Domain Admin Credentials Found', description: 'Finding: CORP\\da.svcadmin plaintext in LSASS / IT-ADMIN-WS-01', types: ['finding'] } as EventData,
    style: { background: 'rgba(244,67,54,0.12)', border: '1.5px solid rgba(244,67,54,0.5)', borderRadius: 24, padding: '10px 18px', color: '#fff', minWidth: 220 },
    sourcePosition: Position.Right, targetPosition: Position.Left },
  { id: 'a9', position: { x: 3060, y: 40 },
    data: { label: 'Netexec LDAP DCSync', injectorType: 'openaev_netexec', attackPattern: 'T1003.006' } as ActionData,
    style: { background: 'rgba(33,150,243,0.12)', border: '1.5px solid rgba(33,150,243,0.5)', borderRadius: 8, padding: '10px 18px', color: '#fff', minWidth: 200 },
    sourcePosition: Position.Right, targetPosition: Position.Left },
  { id: 'e6', type: 'output', position: { x: 3360, y: 40 },
    data: { label: 'Domain Fully Compromised', description: 'Finding: 284 accounts + krbtgt hash extracted. Full domain takeover achieved.', types: ['finding'] } as EventData,
    style: { background: 'rgba(244,67,54,0.14)', border: '2px solid rgba(244,67,54,0.7)', borderRadius: 24, padding: '10px 18px', color: '#fff', minWidth: 220 },
    sourcePosition: Position.Right, targetPosition: Position.Left },
];

const APT_DOMAIN_EDGES: Edge[] = [
  { id: 'e1-a1', source: 'e1', target: 'a1', markerEnd: { type: MarkerType.Arrow }, style: { stroke: '#ff9800' } },
  { id: 'a1-e2', source: 'a1', target: 'e2', markerEnd: { type: MarkerType.Arrow }, style: { stroke: '#2196f3', strokeDasharray: '5 3' } },
  { id: 'e2-a2', source: 'e2', target: 'a2', markerEnd: { type: MarkerType.Arrow }, style: { stroke: '#ff9800' } },
  { id: 'a2-e3', source: 'a2', target: 'e3', markerEnd: { type: MarkerType.Arrow }, style: { stroke: '#2196f3', strokeDasharray: '5 3' } },
  { id: 'e3-a3', source: 'e3', target: 'a3', markerEnd: { type: MarkerType.Arrow }, style: { stroke: '#ff9800' } },
  { id: 'e3-a4', source: 'e3', target: 'a4', markerEnd: { type: MarkerType.Arrow }, style: { stroke: '#ff9800' } },
  { id: 'a3-e4', source: 'a3', target: 'e4', markerEnd: { type: MarkerType.Arrow }, style: { stroke: '#2196f3', strokeDasharray: '5 3' } },
  { id: 'a4-e4', source: 'a4', target: 'e4', markerEnd: { type: MarkerType.Arrow }, style: { stroke: '#2196f3', strokeDasharray: '5 3' } },
  { id: 'e4-a5', source: 'e4', target: 'a5', markerEnd: { type: MarkerType.Arrow }, style: { stroke: '#ff9800' } },
  { id: 'a5-a6', source: 'a5', target: 'a6', markerEnd: { type: MarkerType.Arrow }, style: { stroke: '#2196f3', strokeDasharray: '5 3' }, label: 'NTLM hash' },
  { id: 'a5-a7', source: 'a5', target: 'a7', markerEnd: { type: MarkerType.Arrow }, style: { stroke: '#f44336', strokeDasharray: '5 3' }, label: 'attempt' },
  { id: 'a5-a8', source: 'a5', target: 'a8', markerEnd: { type: MarkerType.Arrow }, style: { stroke: '#2196f3', strokeDasharray: '5 3' }, label: 'PtH' },
  { id: 'a6-e5', source: 'a6', target: 'e5', markerEnd: { type: MarkerType.Arrow }, style: { stroke: '#2196f3', strokeDasharray: '5 3' } },
  { id: 'a8-e5', source: 'a8', target: 'e5', markerEnd: { type: MarkerType.Arrow }, style: { stroke: '#2196f3', strokeDasharray: '5 3' } },
  { id: 'e5-a9', source: 'e5', target: 'a9', markerEnd: { type: MarkerType.Arrow }, style: { stroke: '#ff9800' } },
  { id: 'a9-e6', source: 'a9', target: 'e6', markerEnd: { type: MarkerType.Arrow }, style: { stroke: '#f44336' } },
];

// ── Map scenario ID → graph data ─────────────────────────────────────────────

const SCENARIO_GRAPHS: Record<string, { nodes: MockNode[]; edges: Edge[] }> = {
  'f4bb8b8f-10ad-459b-b629-89dc282a7431': { nodes: FINANCE_NODES,     edges: FINANCE_EDGES },
  '60101396-fb29-4eff-8d0c-1081986c8f5b': { nodes: APT_NODES,         edges: APT_EDGES },
  'b0e28e75-9e66-426a-8a32-80ee097dfdd1': { nodes: ENTERPRISE_NODES,  edges: ENTERPRISE_EDGES },
  'd7f3a2b1-8c4e-4f9a-b2d1-3a5f8e7c6b0a': { nodes: APT_DOMAIN_NODES, edges: APT_DOMAIN_EDGES },
};

// ── Component ────────────────────────────────────────────────────────────────

const ScenarioLogicMock: FunctionComponent = () => {
  const theme = useTheme();
  const { scenarioId } = useParams<{ scenarioId: string }>();

  const graph = SCENARIO_GRAPHS[scenarioId ?? ''];

  if (!graph) {
    return (
      <div style={{ textAlign: 'center', padding: theme.spacing(8, 0), opacity: 0.5 }}>
        <Typography variant="body2">No logic data available for this scenario.</Typography>
      </div>
    );
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      {/* Legend */}
      <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap', paddingBottom: 8 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
          <div style={{ width: 28, height: 12, borderRadius: 12, background: 'rgba(255,152,0,0.3)', border: '1.5px solid rgba(255,152,0,0.6)' }} />
          <Typography variant="caption" color="text.secondary">
            <BoltOutlined sx={{ fontSize: 12, verticalAlign: 'middle', mr: 0.5 }} />
            Event (trigger / finding)
          </Typography>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
          <div style={{ width: 28, height: 12, borderRadius: 3, background: 'rgba(33,150,243,0.3)', border: '1.5px solid rgba(33,150,243,0.6)' }} />
          <Typography variant="caption" color="text.secondary">
            <PlayArrowOutlined sx={{ fontSize: 12, verticalAlign: 'middle', mr: 0.5 }} />
            Action (inject execution)
          </Typography>
        </div>
        <Chip label="Orange arrows = event triggers action" size="small" sx={{ fontSize: 10, height: 20 }} />
        <Chip label="Blue dashed = action triggers event" size="small" sx={{ fontSize: 10, height: 20 }} />
      </div>

      {/* ReactFlow graph */}
      <div style={{ width: '100%', height: 'calc(100vh - 380px)', minHeight: 480 }}>
        <ReactFlow
          nodes={graph.nodes}
          edges={graph.edges}
          fitView
          nodesDraggable={false}
          nodesConnectable={false}
          elementsSelectable={false}
          zoomOnDoubleClick={false}
          proOptions={{ hideAttribution: true }}
        >
          <Background variant={BackgroundVariant.Dots} color={theme.palette.divider} gap={20} />
          <Controls />
          <MiniMap
            nodeColor={(node) => {
              const style = node.style as React.CSSProperties | undefined;
              if (style?.border?.includes('ff9800')) return '#ff9800';
              if (style?.border?.includes('f44336')) return '#f44336';
              return '#2196f3';
            }}
          />
        </ReactFlow>
      </div>
    </div>
  );
};

export default ScenarioLogicMock;
