import { Position } from '@xyflow/react';

import { type Direction } from './algorithms';

export function getSourceHandlePosition(direction: Direction): Position {
  switch (direction) {
    case 'TB':
      return Position.Bottom;
    case 'BT':
      return Position.Top;
    case 'LR':
      return Position.Right;
    case 'RL':
      return Position.Left;
    default:
      return Position.Bottom;
  }
}

export function getTargetHandlePosition(direction: Direction): Position {
  switch (direction) {
    case 'TB':
      return Position.Top;
    case 'BT':
      return Position.Bottom;
    case 'LR':
      return Position.Left;
    case 'RL':
      return Position.Right;
    default:
      return Position.Top;
  }
}

export function getId() {
  return `${Date.now()}`;
}
