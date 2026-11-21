// Circle packing of the estate overview (landing rework, Exposure map concept, #9572): the asset
// groups as bubbles packed around the centre and scaled so the pack fills the canvas, instead of one
// row of bubbles with the canvas empty below. Pure and deterministic, so it is unit tested.

export interface PackInput {
  id: string;
  /** The weight of the bubble (asset count): its area is proportional to it. */
  weight: number;
}

export interface PackedBubble {
  id: string;
  /** Centre, in px from the top left corner of the canvas. */
  x: number;
  y: number;
  r: number;
}

/** Smallest radius of a bubble relative to the largest one, before scaling (a group of 1 stays visible). */
export const PACK_MIN_RATIO = 0.3;
/** Space kept between two bubbles and around the pack, in px after scaling. */
export const PACK_GAP = 12;
/** Angle step and radius step of the spiral search, in radians and in units of the smallest radius. */
const SPIRAL_ANGLE = 0.35;
const SPIRAL_STEP = 0.08;
/** Guard of the spiral search: 40 groups need a few hundred steps, never this many. */
const SPIRAL_MAX = 20000;

const relativeRadius = (weight: number, maxWeight: number): number => {
  if (maxWeight <= 0 || weight <= 0) {
    return PACK_MIN_RATIO;
  }
  return Math.max(PACK_MIN_RATIO, Math.sqrt(Math.min(weight, maxWeight) / maxWeight));
};

/**
 * Packs the bubbles in a unit space (largest radius 1): the largest one in the centre, every next
 * one at the first free spot of a spiral around it, stretched to the canvas aspect ratio so a wide
 * canvas gets a wide pack. Input order is
 * kept as the tie-break, so the same data always draws the same picture.
 */
const packUnit = (inputs: PackInput[], gap: number, aspect: number): PackedBubble[] => {
  const maxWeight = Math.max(0, ...inputs.map(i => i.weight));
  const order = inputs
    .map((input, index) => ({
      ...input,
      index,
      r: relativeRadius(input.weight, maxWeight),
    }))
    .sort((a, b) => b.r - a.r || a.index - b.index);
  const placed: PackedBubble[] = [];
  order.forEach((bubble) => {
    if (placed.length === 0) {
      placed.push({
        id: bubble.id,
        x: 0,
        y: 0,
        r: bubble.r,
      });
      return;
    }
    const free = (x: number, y: number) => placed.every(p => Math.hypot(p.x - x, p.y - y) >= p.r + bubble.r + gap);
    let found = false;
    for (let step = 1; step < SPIRAL_MAX && !found; step += 1) {
      const angle = step * SPIRAL_ANGLE;
      const distance = step * SPIRAL_STEP * PACK_MIN_RATIO;
      const x = Math.cos(angle) * distance * aspect;
      const y = Math.sin(angle) * distance;
      if (free(x, y)) {
        placed.push({
          id: bubble.id,
          x,
          y,
          r: bubble.r,
        });
        found = true;
      }
    }
    if (!found) {
      // Never reached with the API's 40 groups; keeps the function total.
      const right = Math.max(...placed.map(p => p.x + p.r));
      placed.push({
        id: bubble.id,
        x: right + gap + bubble.r,
        y: 0,
        r: bubble.r,
      });
    }
  });
  return placed;
};

/** The pack centred and scaled to a free rectangle of width x height px (see packBubbles). */
const packInCanvas = (inputs: PackInput[], width: number, height: number): PackedBubble[] => {
  if (inputs.length === 0 || width <= 0 || height <= 0) {
    return [];
  }
  // The gap is expressed in unit space from a first estimate of the scale, so it stays about
  // PACK_GAP px once scaled whatever the number of groups.
  const aspect = width / height;
  const estimate = packUnit(inputs, 0.06, aspect);
  const box = (bubbles: PackedBubble[]) => ({
    minX: Math.min(...bubbles.map(b => b.x - b.r)),
    maxX: Math.max(...bubbles.map(b => b.x + b.r)),
    minY: Math.min(...bubbles.map(b => b.y - b.r)),
    maxY: Math.max(...bubbles.map(b => b.y + b.r)),
  });
  const fit = (bounds: ReturnType<typeof box>) => Math.max(0, Math.min(
    (width - 2 * PACK_GAP) / Math.max(bounds.maxX - bounds.minX, 1e-6),
    (height - 2 * PACK_GAP) / Math.max(bounds.maxY - bounds.minY, 1e-6),
  ));
  const firstScale = fit(box(estimate));
  const unitGap = firstScale > 0 ? PACK_GAP / firstScale : 0.06;
  const unit = packUnit(inputs, unitGap, aspect);
  const bounds = box(unit);
  const scale = fit(bounds);
  const offsetX = width / 2 - ((bounds.minX + bounds.maxX) / 2) * scale;
  const offsetY = height / 2 - ((bounds.minY + bounds.maxY) / 2) * scale;
  return unit.map(b => ({
    id: b.id,
    x: offsetX + b.x * scale,
    y: offsetY + b.y * scale,
    r: b.r * scale,
  }));
};

/** A rectangle of the canvas the bubbles keep clear of (a panel floating over it), in px. */
export interface PackObstacle {
  x: number;
  y: number;
  width: number;
  height: number;
}

const overlaps = (bubble: PackedBubble, rect: PackObstacle): boolean => {
  const nearestX = Math.max(rect.x, Math.min(bubble.x, rect.x + rect.width));
  const nearestY = Math.max(rect.y, Math.min(bubble.y, rect.y + rect.height));
  return Math.hypot(bubble.x - nearestX, bubble.y - nearestY) < bubble.r + PACK_GAP / 2;
};

const area = (bubbles: PackedBubble[]) => bubbles.reduce((sum, b) => sum + b.r * b.r, 0);

const shifted = (bubbles: PackedBubble[], dx: number, dy: number): PackedBubble[] => bubbles.map(b => ({
  ...b,
  x: b.x + dx,
  y: b.y + dy,
}));

/**
 * The bubbles packed and scaled to fill a canvas of width x height px: the pack is centred and as
 * large as the canvas allows (its bounding box touches the canvas on its tighter side, PACK_GAP
 * kept around). An empty input or a canvas without size gives no bubble.
 *
 * With an obstacle (the Start here panel floating over the top right of the landing, #9572 UX round 1
 * B3), only the panel's own rectangle is kept clear, never a full-height column: the whole canvas is
 * used when the pack already misses the panel, else the larger of the pack below the panel (full
 * width) and the pack beside it (full height).
 */
export const packBubbles = (inputs: PackInput[], width: number, height: number, obstacle?: PackObstacle | null): PackedBubble[] => {
  const whole = packInCanvas(inputs, width, height);
  if (!obstacle || obstacle.width <= 0 || obstacle.height <= 0 || whole.every(b => !overlaps(b, obstacle))) {
    return whole;
  }
  const below = shifted(packInCanvas(inputs, width, height - (obstacle.y + obstacle.height)), 0, obstacle.y + obstacle.height);
  const beside = obstacle.x > 0 ? packInCanvas(inputs, obstacle.x, height) : [];
  const candidates = [below, beside].filter(c => c.length === inputs.length);
  if (candidates.length === 0) {
    return whole;
  }
  return candidates.reduce((best, c) => (area(c) > area(best) ? c : best));
};
