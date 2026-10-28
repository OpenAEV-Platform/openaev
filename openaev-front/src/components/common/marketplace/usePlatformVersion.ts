import { type LoggedHelper } from '../../../actions/helper';
import { useHelper } from '../../../store';

// Version shown for content shipped with the platform ("Built-in").
const usePlatformVersion = (): string | undefined => useHelper(
  (helper: LoggedHelper) => helper.getPlatformSettings()?.platform_version,
)?.replace('-SNAPSHOT', '');

export default usePlatformVersion;
