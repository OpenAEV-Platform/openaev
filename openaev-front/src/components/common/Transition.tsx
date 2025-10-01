import { Slide } from '@mui/material';
import { type TransitionProps } from '@mui/material/transitions';
import { forwardRef, type JSXElementConstructor, type ReactElement, type Ref } from 'react';

const Transition = forwardRef(
  (
    {
      children,
      ...props
    }: TransitionProps & {
      children: ReactElement<
        unknown,
      string | JSXElementConstructor<unknown>
      >;
    },
    ref: Ref<unknown>,
  ) => {
    return (
      <Slide direction="up" ref={ref} {...props}>
        {children}
      </Slide>
    );
  },
);
Transition.displayName = 'TransitionSlide';

export default Transition;
