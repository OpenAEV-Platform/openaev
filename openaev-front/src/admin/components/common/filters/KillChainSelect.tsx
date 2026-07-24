import { MenuItem, Select, Typography } from '@mui/material';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../components/i18n';

// Well-known kill chains get their official product name; custom ones fall back
// to their raw name (matches the home dashboard matrix + contract picker sidebar).
const KILL_CHAIN_LABELS: Record<string, string> = {
  'mitre-attack': 'MITRE ATT&CK',
  'mitre-atlas': 'MITRE ATLAS',
};
const killChainLabel = (name: string) => KILL_CHAIN_LABELS[name.toLowerCase()] ?? name;

interface KillChainSelectProps {
  killChains: string[];
  value?: string;
  onChange: (killChain: string) => void;
}

/**
 * Compact kill chain switcher designed for the drawer header band: small uppercase caption and
 * an inline select, so the matrix body stays free of chrome. Always visible (like the security
 * coverage widget) so the user knows which kill chain matrix is displayed, even when only one
 * exists.
 */
const KillChainSelect: FunctionComponent<KillChainSelectProps> = ({ killChains, value, onChange }) => {
  const { t } = useFormatter();
  if (killChains.length === 0) {
    return null;
  }
  return (
    <div style={{
      display: 'flex',
      alignItems: 'center',
      gap: 8,
    }}
    >
      <Typography sx={{
        fontFamily: '"Geologica", sans-serif',
        fontSize: 11,
        fontWeight: 600,
        letterSpacing: '0.12em',
        textTransform: 'uppercase',
        color: 'text.secondary',
        whiteSpace: 'nowrap',
      }}
      >
        {t('Kill chain')}
      </Typography>
      <Select
        size="small"
        variant="standard"
        disableUnderline
        value={value ?? ''}
        onChange={e => onChange(e.target.value as string)}
        sx={{
          'fontFamily': '"Geologica", sans-serif',
          'fontWeight': 600,
          'fontSize': 14,
          '& .MuiSelect-select': {
            paddingTop: 0,
            paddingBottom: 0,
          },
        }}
      >
        {killChains.map(chain => (
          <MenuItem key={chain} value={chain}>{killChainLabel(chain)}</MenuItem>
        ))}
      </Select>
    </div>
  );
};

export default KillChainSelect;
