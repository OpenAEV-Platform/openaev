// Preview data until the catalogue API (RFC 0005, PR 3b) exists.
export interface CatalogPayload {
  catalog_item_external_id: string;
  catalog_item_name: string;
  catalog_item_description: string;
  catalog_item_type: string;
  catalog_item_version: string;
  catalog_item_published_at: string;
  catalog_item_author: string;
  catalog_item_platforms: string[];
  catalog_item_domain: string;
}

export const CATALOG_PAYLOADS_PREVIEW: CatalogPayload[] = [
  {
    catalog_item_external_id: 'b3a2c1d0-discovery-whoami',
    catalog_item_name: 'Discover the current user and its privileges',
    catalog_item_description: 'Runs whoami /all to list the user, its groups and privileges, as most intrusions do right after initial access.',
    catalog_item_type: 'Command',
    catalog_item_version: '1.2.0',
    catalog_item_published_at: '2026-10-02T09:00:00Z',
    catalog_item_author: 'Filigran',
    catalog_item_platforms: ['Windows'],
    catalog_item_domain: 'Endpoint',
  },
  {
    catalog_item_external_id: 'c4d5e6f7-credential-lsass',
    catalog_item_name: 'Dump LSASS memory with comsvcs.dll',
    catalog_item_description: 'Creates a minidump of the LSASS process through the built-in comsvcs.dll, a common credential access technique.',
    catalog_item_type: 'Command',
    catalog_item_version: '2.0.1',
    catalog_item_published_at: '2026-09-28T14:30:00Z',
    catalog_item_author: 'Filigran',
    catalog_item_platforms: ['Windows'],
    catalog_item_domain: 'Endpoint',
  },
  {
    catalog_item_external_id: 'd8e9f0a1-dns-exfiltration',
    catalog_item_name: 'Exfiltrate data over DNS queries',
    catalog_item_description: 'Encodes a test file into subdomain labels and resolves them, to check that DNS tunnelling is detected.',
    catalog_item_type: 'DnsResolution',
    catalog_item_version: '1.0.0',
    catalog_item_published_at: '2026-09-20T10:15:00Z',
    catalog_item_author: 'Filigran',
    catalog_item_platforms: ['Windows', 'Linux', 'MacOS'],
    catalog_item_domain: 'Network',
  },
  {
    catalog_item_external_id: 'e2f3a4b5-persistence-cron',
    catalog_item_name: 'Persist with a user crontab entry',
    catalog_item_description: 'Adds a crontab entry that runs a script every minute, then removes it during cleanup.',
    catalog_item_type: 'Command',
    catalog_item_version: '1.1.0',
    catalog_item_published_at: '2026-09-15T08:45:00Z',
    catalog_item_author: 'Atomic Red Team',
    catalog_item_platforms: ['Linux', 'MacOS'],
    catalog_item_domain: 'Endpoint',
  },
  {
    catalog_item_external_id: 'f6a7b8c9-defense-defender',
    catalog_item_name: 'Disable Microsoft Defender real-time protection',
    catalog_item_description: 'Turns real-time protection off with Set-MpPreference, the first step of many ransomware playbooks.',
    catalog_item_type: 'Command',
    catalog_item_version: '1.0.3',
    catalog_item_published_at: '2026-09-11T16:20:00Z',
    catalog_item_author: 'Atomic Red Team',
    catalog_item_platforms: ['Windows'],
    catalog_item_domain: 'Endpoint',
  },
  {
    catalog_item_external_id: 'a0b1c2d3-kerberoasting',
    catalog_item_name: 'Request service tickets for Kerberoasting',
    catalog_item_description: 'Requests TGS tickets for every account with an SPN, to check that ticket requests in bulk raise an alert.',
    catalog_item_type: 'Command',
    catalog_item_version: '1.3.0',
    catalog_item_published_at: '2026-09-05T11:00:00Z',
    catalog_item_author: 'Filigran',
    catalog_item_platforms: ['Windows'],
    catalog_item_domain: 'Endpoint',
  },
  {
    catalog_item_external_id: 'b4c5d6e7-ransomware-encrypt',
    catalog_item_name: 'Encrypt test files like a ransomware',
    catalog_item_description: 'Drops a tool that encrypts a folder of generated test files and renames them, then restores them during cleanup.',
    catalog_item_type: 'Executable',
    catalog_item_version: '3.1.0',
    catalog_item_published_at: '2026-08-30T13:40:00Z',
    catalog_item_author: 'Filigran',
    catalog_item_platforms: ['Windows', 'Linux'],
    catalog_item_domain: 'Endpoint',
  },
  {
    catalog_item_external_id: 'c8d9e0f1-port-scan',
    catalog_item_name: 'Scan internal ports from an endpoint',
    catalog_item_description: 'Sends TCP connection attempts to common ports of the local subnet, as a lateral movement reconnaissance.',
    catalog_item_type: 'NetworkTraffic',
    catalog_item_version: '1.0.0',
    catalog_item_published_at: '2026-08-21T07:55:00Z',
    catalog_item_author: 'Atomic Red Team',
    catalog_item_platforms: ['Windows', 'Linux', 'MacOS'],
    catalog_item_domain: 'Network',
  },
];
