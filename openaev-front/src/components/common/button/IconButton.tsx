import type React from 'react';

import Button, { type CustomButtonProps } from './Button';

const IconButton: React.FC<Omit<CustomButtonProps, 'iconOnly'>> = (props) => {
  return (
    <Button
      variant="tertiary"
      size="small"
      {...(props as CustomButtonProps)}
      iconOnly
    />
  );
};

export default IconButton;
