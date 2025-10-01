import { AccountBalanceOutlined, FireTruckOutlined, ImportantDevicesOutlined, KeyboardVoiceOutlined, QuizOutlined } from '@mui/icons-material';
import { BookOpenBlankVariantOutline, FilterMultipleOutline } from 'mdi-material-ui';
import { type FunctionComponent } from 'react';

interface ItemMainFocusProps {
  mainFocus: string;
  label: string;
  size?: 'small' | 'medium' | 'large' | 'inherit';
}

const renderIcon = (mainFocus: string, size: 'small' | 'medium' | 'large' | 'inherit' | undefined) => {
  switch (mainFocus) {
    case 'endpoint-protection':
      return <ImportantDevicesOutlined fontSize={size ?? 'medium'} style={{ marginRight: 10 }} />;
    case 'web-filtering':
      return <FilterMultipleOutline fontSize={size ?? 'medium'} style={{ marginRight: 10 }} />;
    case 'incident-response':
      return <FireTruckOutlined fontSize={size ?? 'medium'} style={{ marginRight: 10 }} />;
    case 'standard-operating-procedure':
      return <BookOpenBlankVariantOutline fontSize={size ?? 'medium'} style={{ marginRight: 10 }} />;
    case 'crisis-communication':
      return <KeyboardVoiceOutlined fontSize={size ?? 'medium'} style={{ marginRight: 10 }} />;
    case 'strategic-reaction':
      return <AccountBalanceOutlined fontSize={size ?? 'medium'} style={{ marginRight: 10 }} />;
    default:
      return <QuizOutlined fontSize={size ?? 'medium'} style={{ marginRight: 10 }} />;
  }
};

const ItemMainFocus: FunctionComponent<ItemMainFocusProps> = ({
  label,
  mainFocus,
  size,
}) => {
  return (
    <div style={{
      display: 'flex',
      alignItems: 'center',
    }}
    >
      {renderIcon(mainFocus, size)}
      <span style={{
        fontSize: 14,
        whiteSpace: 'nowrap',
        overflow: 'hidden',
        textOverflow: 'ellipsis',
      }}
      >
        {label}
      </span>
    </div>
  );
};

export default ItemMainFocus;
