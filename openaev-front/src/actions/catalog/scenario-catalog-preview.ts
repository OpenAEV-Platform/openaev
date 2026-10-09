// Preview data until the catalogue API (RFC 0005, PR 3b) exists.
export interface CatalogScenario {
  catalog_item_external_id: string;
  catalog_item_name: string;
  catalog_item_description: string;
  catalog_item_version: string;
  catalog_item_published_at: string;
  catalog_item_author: string;
  catalog_item_severity: 'low' | 'medium' | 'high' | 'critical';
  catalog_item_category: string;
  catalog_item_type: 'Time-based' | 'Chained';
  catalog_item_platforms: string[];
  catalog_item_injects_count: number;
}

export const CATALOG_SCENARIOS_PREVIEW: CatalogScenario[] = [
  {
    catalog_item_external_id: 'sc-ransomware-lockbit',
    catalog_item_name: 'LockBit-style ransomware intrusion',
    catalog_item_description: 'From phishing access to defense evasion, credential dumping and file encryption, as seen in LockBit campaigns.',
    catalog_item_version: '2.1.0',
    catalog_item_published_at: '2026-10-03T09:00:00Z',
    catalog_item_author: 'Filigran',
    catalog_item_severity: 'critical',
    catalog_item_category: 'attack-scenario',
    catalog_item_type: 'Chained',
    catalog_item_platforms: ['Windows'],
    catalog_item_injects_count: 14,
  },
  {
    catalog_item_external_id: 'sc-apt29-discovery',
    catalog_item_name: 'APT29 discovery and collection',
    catalog_item_description: 'Reproduces the discovery and collection techniques reported for APT29 after initial access.',
    catalog_item_version: '1.3.0',
    catalog_item_published_at: '2026-09-27T14:00:00Z',
    catalog_item_author: 'Filigran',
    catalog_item_severity: 'high',
    catalog_item_category: 'attack-scenario',
    catalog_item_type: 'Time-based',
    catalog_item_platforms: ['Windows', 'Linux'],
    catalog_item_injects_count: 9,
  },
  {
    catalog_item_external_id: 'sc-data-exfiltration-cloud',
    catalog_item_name: 'Data exfiltration to cloud storage',
    catalog_item_description: 'Stages sensitive-looking files and sends them to public cloud storage over HTTPS and DNS.',
    catalog_item_version: '1.0.2',
    catalog_item_published_at: '2026-09-18T10:30:00Z',
    catalog_item_author: 'Filigran',
    catalog_item_severity: 'high',
    catalog_item_category: 'data-exfiltration',
    catalog_item_type: 'Time-based',
    catalog_item_platforms: ['Windows', 'Linux', 'MacOS'],
    catalog_item_injects_count: 6,
  },
  {
    catalog_item_external_id: 'sc-lateral-movement-smb',
    catalog_item_name: 'Lateral movement over SMB and WMI',
    catalog_item_description: 'Moves from a first endpoint to its neighbours with remote services and WMI, then checks detection on each hop.',
    catalog_item_version: '1.1.0',
    catalog_item_published_at: '2026-09-09T08:15:00Z',
    catalog_item_author: 'Community',
    catalog_item_severity: 'medium',
    catalog_item_category: 'lateral-movement',
    catalog_item_type: 'Chained',
    catalog_item_platforms: ['Windows'],
    catalog_item_injects_count: 8,
  },
  {
    catalog_item_external_id: 'sc-url-filtering-baseline',
    catalog_item_name: 'URL filtering baseline',
    catalog_item_description: 'Requests known malicious, newly registered and uncategorised domains to measure what the proxy blocks.',
    catalog_item_version: '1.0.0',
    catalog_item_published_at: '2026-08-29T16:45:00Z',
    catalog_item_author: 'Filigran',
    catalog_item_severity: 'low',
    catalog_item_category: 'url-filtering',
    catalog_item_type: 'Time-based',
    catalog_item_platforms: ['Windows', 'Linux', 'MacOS'],
    catalog_item_injects_count: 12,
  },
  {
    catalog_item_external_id: 'sc-log4shell-exploitation',
    catalog_item_name: 'Log4Shell exploitation chain',
    catalog_item_description: 'Exploits a vulnerable Java service, drops a reverse shell and runs post-exploitation commands.',
    catalog_item_version: '3.0.0',
    catalog_item_published_at: '2026-08-20T11:20:00Z',
    catalog_item_author: 'Community',
    catalog_item_severity: 'critical',
    catalog_item_category: 'vulnerability-exploitation',
    catalog_item_type: 'Chained',
    catalog_item_platforms: ['Linux'],
    catalog_item_injects_count: 7,
  },
];
