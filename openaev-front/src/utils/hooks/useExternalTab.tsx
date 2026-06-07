import { useCallback, useEffect, useRef, useState } from 'react';

interface UseExternalTabProps {
  url: string;
  tabName: string;
  onMessage: (event: MessageEvent) => void;
  onClosingTab: () => void;
}

interface UseExternalTabReturn {
  isTabOpen: boolean;
  openTab: () => void;
  closeTab: () => void;
  focusTab: () => void;
}

const useExternalTab = ({
  url,
  tabName,
  onMessage,
  onClosingTab,
}: UseExternalTabProps): UseExternalTabReturn => {
  const tabRef = useRef<WindowProxy | null>(null);
  const [isTabOpen, setIsTabOpen] = useState(false);

  // Keep the latest callbacks in refs so the message listener and the
  // tab-closed polling interval always invoke the current closures without
  // re-subscribing on every render (which would otherwise reset the 500ms
  // interval whenever the caller passes new inline callbacks).
  const onMessageRef = useRef(onMessage);
  const onClosingTabRef = useRef(onClosingTab);
  useEffect(() => {
    onMessageRef.current = onMessage;
    onClosingTabRef.current = onClosingTab;
  }, [onMessage, onClosingTab]);

  const beforeUnloadHandler = (event: BeforeUnloadEvent) => {
    event.preventDefault();
    return null;
  };

  const openTab = useCallback(() => {
    setIsTabOpen(true);
    tabRef.current = window.open(url, tabName);
  }, [url, tabName]);

  const closeTab = useCallback(() => {
    tabRef.current?.close();
    tabRef.current = null;
    setIsTabOpen(false);
  }, []);

  const focusTab = useCallback(() => {
    tabRef.current?.focus();
  }, []);

  useEffect(() => {
    const handleMessage = (event: MessageEvent) => {
      if (event.source === tabRef.current) {
        const closingTabEvent = ['cancel', 'register', 'unregister'];
        if (closingTabEvent.includes(event.data.action)) {
          closeTab();
        }
        onMessageRef.current(event);
      }
    };
    let checkInterval: ReturnType<typeof setInterval> | undefined;
    if (isTabOpen) {
      window.addEventListener('message', handleMessage);
      window.addEventListener('beforeunload', beforeUnloadHandler);
      checkInterval = setInterval(() => {
        if (tabRef.current?.closed) {
          onClosingTabRef.current();
          closeTab();
          clearInterval(checkInterval);
        }
      }, 500);
    }

    return () => {
      // Compare against undefined rather than truthiness: a valid timer handle
      // can be 0 in some implementations/polyfills.
      if (checkInterval !== undefined) {
        clearInterval(checkInterval);
      }
      window.removeEventListener('message', handleMessage);
      window.removeEventListener('beforeunload', beforeUnloadHandler);
    };
  }, [isTabOpen, closeTab]);

  return {
    isTabOpen,
    openTab,
    closeTab,
    focusTab,
  };
};

export default useExternalTab;
