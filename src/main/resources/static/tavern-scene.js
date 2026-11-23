import * as THREE from './vendor/three.module.js';
import { findRoute } from './path.js';

const movement = {
  KeyW: [0, -1], ArrowUp: [0, -1],
  KeyS: [0, 1], ArrowDown: [0, 1],
  KeyA: [-1, 0], ArrowLeft: [-1, 0],
  KeyD: [1, 0], ArrowRight: [1, 0],
};

export function buildTavern(world, markers, stops, openStop, onUnavailable) {
  const renderer = new THREE.WebGLRenderer({ antialias: true });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.75));
  renderer.setClearColor(0x251a17);
  renderer.domElement.setAttribute('aria-hidden', 'true');
  world.prepend(renderer.domElement);
  const scene = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(43, 1, .1, 65);
  scene.add(new THREE.HemisphereLight(0xffe6be, 0x75494a, 2.1));
  const sun = new THREE.DirectionalLight(0xffd2a0, 2.1);
  sun.position.set(-5, 8, 5);
  scene.add(sun);
  const room = createRoom(scene);
  const player = createAdventurer(scene);
  player.position.set(-2.75, 0, 2.4);
  const keys = new Set();
  const projected = new THREE.Vector3();
  const step = new THREE.Vector2();
  const view = { paused: false, onNearStop: null, clearKeys: () => keys.clear() };
  let near;
  let previousTime;
  let available = true;
  let canvasWidth;
  let canvasHeight;
  let route = [];
  let arrival;
  const ray = new THREE.Raycaster();
  const floor = new THREE.Plane(new THREE.Vector3(0, 1, 0), 0);
  const destination = new THREE.Vector3();

  function canWalk(x, z) {
    return Math.abs(x) < 5.55 && z > -3.98 && z < 3.9 &&
      !room.obstacles.some(o => Math.abs(x - o.x) < o.halfWidth + .2 &&
        Math.abs(z - o.z) < o.halfDepth + .2);
  }

  function navigate(target, stop) {
    if (!available || view.paused) return;
    const path = findRoute(player.position, target, canWalk, Boolean(stop));
    if (!path) return;
    keys.clear();
    route = path;
    arrival = stop;
  }

  renderer.domElement.addEventListener('click', event => {
    const bounds = renderer.domElement.getBoundingClientRect();
    ray.setFromCamera({
      x: (event.clientX - bounds.left) / bounds.width * 2 - 1,
      y: 1 - (event.clientY - bounds.top) / bounds.height * 2,
    }, camera);
    if (ray.ray.intersectPlane(floor, destination) && canWalk(destination.x, destination.z)) navigate(destination);
  });

  const locations = Object.entries(stops).map(([id, stop]) => {
    const marker = document.createElement('button');
    marker.type = 'button';
    marker.className = 'marker';
    marker.textContent = stop.label;
    marker.addEventListener('click', () => navigate(stop, id));
    markers.append(marker);
    return { id, ...stop, marker };
  });

  function resize() {
    canvasWidth = world.clientWidth;
    canvasHeight = world.clientHeight;
    camera.aspect = canvasWidth / Math.max(canvasHeight, 1);
    const zoom = Math.min(2.1, Math.max(1, 1.32 / camera.aspect));
    camera.position.set(7.9 * zoom, 11.5 * zoom, 15.2 * zoom);
    camera.lookAt(0, .35, -.4);
    camera.updateProjectionMatrix();
    renderer.setSize(canvasWidth, canvasHeight, false);
  }

  function walk(dt) {
    step.set(0, 0);
    for (const key of keys) {
      const [x, z] = movement[key];
      step.x += x;
      step.y += z;
    }
    const pos = player.position;
    if (!step.lengthSq()) {
      const point = route[0];
      if (point) {
        step.set(point.x - pos.x, point.z - pos.z);
        if (step.length() <= 3.35 * dt) {
          if (canWalk(point.x, point.z)) {
            pos.x = point.x;
            pos.z = point.z;
            route.shift();
          } else {
            route = [];
            arrival = undefined;
          }
          return;
        }
      } else {
        if (arrival) {
          const id = arrival;
          arrival = undefined;
          openStop(id);
        }
        return;
      }
    }
    step.normalize().multiplyScalar(3.35 * dt);
    const beforeX = pos.x;
    const beforeZ = pos.z;
    if (canWalk(pos.x + step.x, pos.z)) pos.x += step.x;
    if (canWalk(pos.x, pos.z + step.y)) pos.z += step.y;
    if (route.length && pos.x === beforeX && pos.z === beforeZ) {
      route = [];
      arrival = undefined;
    }
    player.rotation.y = Math.atan2(step.x, step.y);
  }

  function updateMarkers() {
    let closest;
    let distance = 1.65;
    for (const stop of locations) {
      projected.set(stop.x, stop.height, stop.z).project(camera);
      stop.marker.style.left = `${(projected.x + 1) * canvasWidth / 2}px`;
      stop.marker.style.top = `${(1 - projected.y) * canvasHeight / 2}px`;
      const gap = Math.hypot(player.position.x - stop.x, player.position.z - stop.z);
      if (gap < distance) {
        distance = gap;
        closest = stop.id;
      }
    }
    if (closest !== near) {
      near = closest;
      locations.forEach(stop => stop.marker.classList.toggle('is-close', stop.id === near));
      view.onNearStop?.(near);
    }
  }

  document.addEventListener('keydown', event => {
    if (!available || view.paused || event.altKey || event.ctrlKey || event.metaKey ||
        !Object.hasOwn(movement, event.code)) return;
    event.preventDefault();
    route = [];
    arrival = undefined;
    keys.add(event.code);
  });
  document.addEventListener('keyup', event => keys.delete(event.code));
  window.addEventListener('blur', view.clearKeys);
  renderer.domElement.addEventListener('webglcontextlost', () => {
    available = false;
    view.clearKeys();
    renderer.setAnimationLoop(null);
    renderer.domElement.hidden = true;
    onUnavailable();
  });
  new ResizeObserver(resize).observe(world);
  resize();
  renderer.setAnimationLoop(time => {
    const dt = previousTime === undefined ? 0 : Math.min((time - previousTime) / 1000, .045);
    previousTime = time;
    if (!view.paused) walk(dt);
    renderer.render(scene, camera);
    updateMarkers();
  });
  return view;
}

const cube = new THREE.BoxGeometry(1, 1, 1);
const materials = new Map();

function material(color) {
  if (!materials.has(color)) materials.set(color, new THREE.MeshStandardMaterial({ color, roughness: .85 }));
  return materials.get(color);
}

function block(parent, color, x, y, z, width, height, depth) {
  const mesh = new THREE.Mesh(cube, material(color));
  mesh.position.set(x, y, z);
  mesh.scale.set(width, height, depth);
  parent.add(mesh);
  return mesh;
}

function createRoom(scene) {
  const timber = 0x462921;
  const oak = 0x734428;
  const stone = 0x74625a;
  const obstacles = [];
  const box = (color, ...args) => block(scene, color, ...args);
  box(0x36211d, 0, -.32, 0, 12.35, .56, 8.85);
  const planks = [0x8d5d3e, 0x9b6948, 0x805236, 0x9a6340, 0x83543e];
  for (let row = 0; row < 18; row++) {
    for (let col = 0; col < 6; col++) {
      box(planks[(row * 7 + col * 11) % planks.length], -5 + col * 2, -.035, -4.08 + row * .48,
        1.98, .11, .46);
    }
  }
  box(0xb39a78, 0, 1.55, -4.36, 12.2, 3.1, .24);
  box(0xb39a78, -6, 1.55, 0, .24, 3.1, 8.5);
  for (const y of [.16, 1.1, 3.1]) {
    box(timber, 0, y, -4.19, 12.3, .22, .18);
    box(timber, -5.86, y, 0, .18, .22, 8.5);
  }
  for (const x of [-5.85, -2, 1.9, 5.85]) box(timber, x, 1.55, -4.17, .24, 3.1, .2);
  for (const z of [-1.2, 1.6, 4.05]) box(timber, -5.84, 1.55, z, .2, 3.1, .24);

  // Tablón de pergaminos y chimenea al fondo.
  box(timber, -3.5, 1.74, -4.01, 2.45, 1.62, .22);
  box(oak, -3.5, 1.74, -3.88, 2.25, 1.42, .08);
  for (const [i, x] of [-4.17, -3.52, -2.85].entries()) {
    const notice = box(0xe3c694, x, 1.7 + (i % 2) * .18, -3.82, .46, .67, .035);
    notice.rotation.z = (i - 1) * .12;
    for (let j = 0; j < 3; j++) {
      box(0x97724f, x, 1.77 + (i % 2) * .18 - j * .115, -3.793, .27, .024, .012);
    }
  }
  for (const x of [1.95, 3.65]) box(stone, x, .64, -3.82, .48, 1.28, .62);
  box(stone, 2.8, 1.32, -3.81, 2.35, .28, .72);
  box(stone, 2.8, .085, -3.65, 2.35, .16, 1.1);
  box(0x2b201b, 2.8, .6, -4.05, 1.3, 1, .27);
  for (const [i, x] of [2.5, 2.78, 3.03].entries()) {
    const flame = new THREE.Mesh(new THREE.ConeGeometry(.18, .4 + i * .08, 5),
      new THREE.MeshBasicMaterial({ color: i === 1 ? 0xffc15b : 0xff722f }));
    flame.position.set(x, .38 + i * .035, -3.62);
    scene.add(flame);
  }
  const firelight = new THREE.PointLight(0xff9f44, 25, 7);
  firelight.position.set(2.8, 1, -3.45);
  scene.add(firelight);
  obstacles.push({ x: 2.8, z: -3.72, halfWidth: 1.2, halfDepth: .6 });

  function table(x, z, width, depth) {
    box(oak, x, .98, z, width, .16, depth);
    for (const dx of [-1, 1]) {
      for (const dz of [-1, 1]) {
        box(timber, x + dx * (width / 2 - .16), .44, z + dz * (depth / 2 - .16), .16, .88, .16);
      }
    }
    obstacles.push({ x, z, halfWidth: width / 2, halfDepth: depth / 2 });
  }

  box(0x733633, 2.6, .025, -.4, 3.95, .026, 2.8);
  for (const z of [-1.68, .88]) box(0xcda366, 2.6, .043, z, 3.75, .016, .095);
  table(2.6, -.4, 2.6, 1.5);
  box(0xd2b27e, 2.57, 1.07, -.44, 1.2, .027, .8).rotation.y = -.14;
  box(0x795f43, 2.55, 1.09, -.44, .54, .015, .035).rotation.y = .5;
  for (const z of [-1.5, .7]) {
    box(timber, 2.6, .4, z, 1.55, .13, .38);
    for (const x of [2.03, 3.17]) box(timber, x, .2, z, .15, .4, .2);
    obstacles.push({ x: 2.6, z, halfWidth: .78, halfDepth: .19 });
  }
  table(-4.15, .4, 1.2, .9);
  box(0x782d2b, -4.15, 1.09, .4, .62, .07, .47);
  box(0xebd4ab, -4.15, 1.137, .4, .56, .042, .43);
  box(0x996744, -4.15, 1.163, .4, .026, .014, .43);
  for (const [x, z] of [[-5.1, -2.85], [-5.1, 2.9], [4.9, -3.15]]) {
    const barrel = new THREE.Mesh(new THREE.CylinderGeometry(.36, .31, .8, 10), material(oak));
    barrel.position.set(x, .42, z);
    scene.add(barrel);
    for (const y of [.23, .64]) {
      const band = new THREE.Mesh(new THREE.CylinderGeometry(.37, .37, .065, 10), material(timber));
      band.position.set(x, y, z);
      scene.add(band);
    }
    obstacles.push({ x, z, halfWidth: .37, halfDepth: .37 });
  }
  return { obstacles };
}

function createAdventurer(scene) {
  const player = new THREE.Group();
  block(player, 0x573027, -.13, .18, 0, .19, .32, .22);
  block(player, 0x573027, .13, .18, 0, .19, .32, .22);
  block(player, 0x286e65, 0, .53, 0, .47, .48, .32);
  block(player, 0x42252b, 0, .55, -.18, .48, .61, .1);
  block(player, 0xc99b75, 0, .89, .02, .32, .29, .29);
  block(player, 0x30736a, 0, 1.025, -.025, .38, .13, .36);
  block(player, 0xd4a258, 0, .49, .17, .15, .08, .04);
  scene.add(player);
  return player;
}
