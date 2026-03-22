import { useTheme } from '@mui/material';
import React, { type CSSProperties, type FunctionComponent } from 'react';

const PageContainerContext = React.createContext({ inPageContainer: false });

interface PageContainerProps {
  children: React.ReactNode;
  withRightMenu?: boolean;
  withGap?: boolean;
  style?: CSSProperties;
}

const PageContainer: FunctionComponent<PageContainerProps> = ({
  children,
  withRightMenu = false,
  withGap = false,
  style = {},
}) => {
  const theme = useTheme();
  return (
    <PageContainerContext.Provider value={{ inPageContainer: true }}>
      <div
        style={{
          margin: 0,
          padding: 0,
          paddingRight: withRightMenu ? '200px' : undefined,
          display: 'flex',
          flexDirection: 'column',
          gap: withGap ? theme.spacing(3) : undefined,
          ...style,
        }}
      >
        {children}
      </div>
    </PageContainerContext.Provider>
  );
};

export default PageContainer;
