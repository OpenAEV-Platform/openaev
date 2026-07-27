// Well-known kill chains get their official product name; custom ones fall back
// to their raw name (matches the home dashboard matrix + contract picker sidebar).
const KILL_CHAIN_LABELS: Record<string, string> = {
  'mitre-attack': 'MITRE ATT&CK',
  'mitre-atlas': 'MITRE ATLAS',
};

const killChainLabel = (name: string) => KILL_CHAIN_LABELS[name.toLowerCase()] ?? name;

export default killChainLabel;
