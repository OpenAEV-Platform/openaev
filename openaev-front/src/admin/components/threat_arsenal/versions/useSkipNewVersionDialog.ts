import { useLocalStorage } from 'usehooks-ts';

const SKIP_NEW_VERSION_DIALOG_KEY = 'threat-arsenal:skip-new-version-dialog';

/** "Don't ask me again": no New version dialog after a save; the Versions tab turns it back on. */
const useSkipNewVersionDialog = () => useLocalStorage<boolean>(SKIP_NEW_VERSION_DIALOG_KEY, false);

export default useSkipNewVersionDialog;
