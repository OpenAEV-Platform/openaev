import { CircularProgress } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { width } from '@mui/system';
import { FiligranLoader } from 'filigran-icon';

import type { LoggedHelper } from '../../../actions/helper';
import { useHelper } from '../../../store';
import type { PlatformSettings } from '../../../utils/api-types';

interface Props {
  size?: 's' | 'm' | 'l';
  variant?: 'container' | 'inElement';
}

const Loader = ({ size = 'm', variant = 'container' }: Props) => {
  const theme = useTheme();
  const { settings }: { settings: PlatformSettings } = useHelper((helper: LoggedHelper) => ({ settings: helper.getPlatformSettings() }));
  const hasFiligranLoader = theme && !(settings?.platform_license?.license_is_validated && settings?.platform_whitemark);

  const getSize = (size: 'xs' | 's' | 'm' | 'l') => {
    if (size === 'xs') {
      return 15;
    } else if (size === 's') {
      return 24;
    } else if (size === 'l') {
      return 80;
    }
    return 40;
  };

  return (

    <div style={{
      width: '-webkit-fill-available',
      textAlign: 'center',
      top: variant === 'container' ? '46%' : '0',
      position: variant === 'container' ? 'absolute' : 'initial',
      height: variant === 'container' ? 'calc(100vh-180px)' : '100%',
      display: variant === 'container' ? '' : 'flex',
      alignItems: 'center',
      justifyContent: 'center',
    }}
    >
      {!hasFiligranLoader ? (
        <FiligranLoader height={getSize(size)} color={theme.palette?.grey.A100} />
      ) : (
        <CircularProgress size={getSize(size)} thickness={1} />
      )}
    </div>
  );
};

export default Loader;
