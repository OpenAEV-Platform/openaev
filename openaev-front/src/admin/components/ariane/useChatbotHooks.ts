import { useContext } from 'react';

import { ChatbotContext, type ChatbotContextType, SIDEBAR_GAP } from './chatbotContext';

export const useChatbot = (): ChatbotContextType => {
  const context = useContext(ChatbotContext);
  if (!context) {
    throw new Error('useChatbot must be used within a ChatbotProvider');
  }
  return context;
};

export const useChatbotContentMargin = (): number => {
  const context = useContext(ChatbotContext);
  if (!context) return 0;
  const { isOpen, mode, sidebarWidth } = context;
  if (isOpen && mode === 'sidebar') {
    return sidebarWidth + SIDEBAR_GAP;
  }
  return 0;
};

interface TransitionTheme {
  transitions: {
    create: (props: string | string[], options?: {
      easing?: string;
      duration?: number;
    }) => string;
    easing: { easeInOut: string };
    duration: { enteringScreen: number };
  };
}

export const useChatbotContentTransition = (theme: TransitionTheme): string => {
  const context = useContext(ChatbotContext);
  if (!context) return 'none';
  const { isResizing } = context;
  if (isResizing) return 'none';
  return theme.transitions.create(['margin-right'], {
    easing: theme.transitions.easing.easeInOut,
    duration: theme.transitions.duration.enteringScreen,
  });
};
