import { Position } from '@xyflow/react';

import { type Direction } from './algorithms';

// eslint-disable-next-line consistent-return
export function getSourceHandlePosition(direction: Direction) {
  // eslint-disable-next-line default-case
  switch (direction) {
    case 'TB':
      return Position.Bottom;
    case 'BT':
      return Position.Top;
    case 'LR':
      return Position.Right;
    case 'RL':
      return Position.Left;
  }
}

// eslint-disable-next-line consistent-return
export function getTargetHandlePosition(direction: Direction) {
  // eslint-disable-next-line default-case
  switch (direction) {
    case 'TB':
      return Position.Top;
    case 'BT':
      return Position.Bottom;
    case 'LR':
      return Position.Left;
    case 'RL':
      return Position.Right;
  }
}

export function getId() {
  return `${Date.now()}`;
}
