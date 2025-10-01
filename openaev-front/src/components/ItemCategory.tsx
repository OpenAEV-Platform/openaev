import { AdsClickOutlined, AppsOutlined, BlurLinearOutlined, CrisisAlertOutlined, NewspaperOutlined, TourOutlined } from '@mui/icons-material';
import { AirFilter, CarShiftPattern, CrosshairsQuestion, DatabaseEyeOutline } from 'mdi-material-ui';
import { type FunctionComponent } from 'react';

interface ItemCategoryProps {
  category: string;
  label?: string;
  size?: 'small' | 'medium' | 'large' | 'inherit';
}

const renderIcon = (category: string, size: 'small' | 'medium' | 'large' | 'inherit' | undefined) => {
  switch (category) {
    case 'global-crisis':
      return <CrisisAlertOutlined fontSize={size ?? 'medium'} style={{ marginRight: 10 }} />;
    case 'attack-scenario':
      return <BlurLinearOutlined fontSize={size ?? 'medium'} style={{ marginRight: 10 }} />;
    case 'media-pressure':
      return <NewspaperOutlined fontSize={size ?? 'medium'} style={{ marginRight: 10 }} />;
    case 'data-exfiltration':
      return <DatabaseEyeOutline fontSize={size ?? 'medium'} style={{ marginRight: 10 }} />;
    case 'capture-the-flag':
      return <TourOutlined fontSize={size ?? 'medium'} style={{ marginRight: 10 }} />;
    case 'vulnerability-exploitation':
      return <AdsClickOutlined fontSize={size ?? 'medium'} style={{ marginRight: 10 }} />;
    case 'lateral-movement':
      return <CarShiftPattern fontSize={size ?? 'medium'} style={{ marginRight: 10 }} />;
    case 'url-filtering':
      return <AirFilter fontSize={size ?? 'medium'} style={{ marginRight: 10 }} />;
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
