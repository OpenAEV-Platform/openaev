import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@filigran/design-system';
import { Typography } from '@mui/material';
import { type FunctionComponent } from 'react';

import { useFormatter } from '../../../../components/i18n';
import killChainLabel from './killChainLabel';

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
        value={value ?? ''}
        onValueChange={next => onChange(next as string)}
      >
        <SelectTrigger>
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          {killChains.map(chain => (
            <SelectItem key={chain} value={chain}>{killChainLabel(chain)}</SelectItem>
          ))}
        </SelectContent>
      </Select>
    </div>
  );
};

export default KillChainSelect;
