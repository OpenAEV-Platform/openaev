import type { ChatMode } from '@filigran/chatbot';
import { createContext } from 'react';

export interface ChatbotContextType {
  isOpen: boolean;
  mode: ChatMode;
  sidebarWidth: number;
  isResizing: boolean;
  openChat: () => void;
  closeChat: () => void;
  toggleChat: () => void;
  setMode: (mode: ChatMode) => void;
  setSidebarWidth: (width: number) => void;
  setIsResizing: (isResizing: boolean) => void;
}

export const ChatbotContext = createContext<ChatbotContextType | null>(null);

export const SIDEBAR_GAP = 6;
