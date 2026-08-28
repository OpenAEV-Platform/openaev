import { AppsOutlined, CampaignOutlined, FilterAltOutlined, FlagOutlined, GpsFixedOutlined, PublicOutlined, SwapHorizOutlined } from '@mui/icons-material';
import { CrosshairsQuestion, DatabaseExportOutline, ShieldBugOutline } from 'mdi-material-ui';
import { type FunctionComponent } from 'react';

interface ItemCategoryProps {
  category: string;
  label?: string;
  size?: 'small' | 'medium' | 'large' | 'inherit';
}

// One icon per scenario category, chosen to read at a glance:
// - global-crisis: a globe (world-wide crisis)
// - attack-scenario: a crosshair on target (a targeted attack)
// - media-pressure: a megaphone (public / press pressure)
// - data-exfiltration: data leaving a database (export)
// - capture-the-flag: a flag (CTF)
// - vulnerability-exploitation: a shield with a bug (exploited weakness)
// - lateral-movement: horizontal swap arrows (host-to-host movement)
// - url-filtering: a filter funnel (web content filtering)
const renderIcon = (category: string, size: 'small' | 'medium' | 'large' | 'inherit' | undefined) => {
  switch (category) {
    case 'global-crisis':
      return <PublicOutlined fontSize={size ?? 'medium'} style={{ marginRight: 10 }} />;
    case 'attack-scenario':
      return <GpsFixedOutlined fontSize={size ?? 'medium'} style={{ marginRight: 10 }} />;
    case 'media-pressure':
      return <CampaignOutlined fontSize={size ?? 'medium'} style={{ marginRight: 10 }} />;
    case 'data-exfiltration':
      return <DatabaseExportOutline fontSize={size ?? 'medium'} style={{ marginRight: 10 }} />;
    case 'capture-the-flag':
      return <FlagOutlined fontSize={size ?? 'medium'} style={{ marginRight: 10 }} />;
    case 'vulnerability-exploitation':
      return <ShieldBugOutline fontSize={size ?? 'medium'} style={{ marginRight: 10 }} />;
    case 'lateral-movement':
      return <SwapHorizOutlined fontSize={size ?? 'medium'} style={{ marginRight: 10 }} />;
    case 'url-filtering':
      return <FilterAltOutlined fontSize={size ?? 'medium'} style={{ marginRight: 10 }} />;
    case 'all':
      return <AppsOutlined fontSize={size ?? 'medium'} style={{ marginRight: 10 }} />;
    default:
      return <CrosshairsQuestion fontSize={size ?? 'medium'} style={{ marginRight: 10 }} />;
  }
};

const ItemCategory: FunctionComponent<ItemCategoryProps> = ({
  label,
  category,
  size,
}) => {
  return (
    <div style={{
      display: 'flex',
      alignItems: 'center',
    }}
    >
      {renderIcon(category, size)}
      {label && (
        <span style={{
          fontSize: 14,
          whiteSpace: 'nowrap',
          overflow: 'hidden',
          textOverflow: 'ellipsis',
        }}
        >
          {label}
        </span>
      )}
    </div>
  );
};

export default ItemCategory;
