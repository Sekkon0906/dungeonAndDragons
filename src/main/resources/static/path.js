const size = .25;
const columns = 44;
const rows = 31;
const originX = -5.375;
const originZ = -3.875;
const square = (x, y) => ({ x: originX + x * size, z: originZ + y * size });

export function findRoute(start, target, canWalk, interaction = false) {
  const index = (x, y) => y * columns + x;
  const nearest = (value, origin, limit) => Math.max(0, Math.min(limit - 1, Math.round((value - origin) / size)));
  const first = index(nearest(start.x, originX, columns), nearest(start.z, originZ, rows));
  const goal = index(nearest(target.x, originX, columns), nearest(target.z, originZ, rows));
  const previous = new Int32Array(columns * rows).fill(-1);
  const queue = [first];
  previous[first] = first;

  for (let head = 0; head < queue.length; head++) {
    const id = queue[head];
    const x = id % columns;
    const y = Math.floor(id / columns);
    const point = square(x, y);
    const arrived = interaction ? Math.hypot(point.x - target.x, point.z - target.z) < 1.45 : id === goal;
    if (arrived) {
      const route = [];
      for (let next = id; next !== first; next = previous[next]) {
        route.push(square(next % columns, Math.floor(next / columns)));
      }
      return route.reverse();
    }
    for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
      const nx = x + dx;
      const ny = y + dy;
      if (nx < 0 || nx >= columns || ny < 0 || ny >= rows) continue;
      const next = index(nx, ny);
      const candidate = square(nx, ny);
      if (previous[next] !== -1 || !canWalk(candidate.x, candidate.z)) continue;
      if (!canWalk((point.x + candidate.x) / 2, (point.z + candidate.z) / 2)) continue;
      previous[next] = id;
      queue.push(next);
    }
  }
  return null;
}
