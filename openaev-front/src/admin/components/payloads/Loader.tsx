import { CircularProgress } from '@mui/material';
import { useTheme } from '@mui/material/styles';
import { FiligranLoader } from 'filigran-icon';

import type { LoggedHelper } from '../../../actions/helper';
import { useHelper } from '../../../store';
import type { PlatformSettings } from '../../../utils/api-types';

const Loader = () => {
  const theme = useTheme();
  const { settings }: { settings: PlatformSettings } = useHelper((helper: LoggedHelper) => ({ settings: helper.getPlatformSettings() }));
  const hasFiligranLoader = theme && !(settings?.platform_license?.license_is_validated && settings?.platform_whitemark);

  return (
    <>
      {!hasFiligranLoader ? (
        <FiligranLoader height={24} color={theme?.palette?.grey.A100} />
      ) : (
        <CircularProgress size={24} thickness={1} />
      )}
    </>
  );
};

export default Loader;
