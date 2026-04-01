/// <reference types="vite/client" />

declare module '*.svg' {
  const content: string;
  export default content;
}

interface Window {
  BASE_PATH?: string;
  ENABLED_DEV_FEATURES?: string;
  __assetsPath?: (filename: string) => string;
}
