import { useSearchParams } from 'react-router';

// DESIGN DEMO ONLY — remove before merging. Lets the same build show the two options compared with
// the PO: "queue" (To review / My changes tabs) and "filter" (no new tab; the Hero tiles filter the
// list). Set it once per browser with ?design=queue or ?design=filter on any Threat Arsenal page.
export type ApprovalDesignVariant = 'queue' | 'filter';

const STORAGE_KEY = 'approval-design-variant';

const useApprovalDesignVariant = (): ApprovalDesignVariant => {
  const [searchParams] = useSearchParams();
  const fromUrl = searchParams.get('design');
  if (fromUrl === 'queue' || fromUrl === 'filter') {
    try {
      window.localStorage.setItem(STORAGE_KEY, fromUrl);
    } catch {
      // Storage unavailable: the URL still decides for this page.
    }
    return fromUrl;
  }
  try {
    return window.localStorage.getItem(STORAGE_KEY) === 'filter' ? 'filter' : 'queue';
  } catch {
    return 'queue';
  }
};

export default useApprovalDesignVariant;
