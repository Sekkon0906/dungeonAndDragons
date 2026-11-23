const width = 6;
const height = 5;
const walls = new Set([8, 14, 21]);
const distance = (a, b) => Math.abs(a % width - b % width) +
  Math.abs(Math.floor(a / width) - Math.floor(b / width));

function reachable(start, blocked, limit) {
  const costs = new Map([[start, 0]]);
  const queue = [start];
  for (const current of queue) {
    const cost = costs.get(current);
    if (cost >= limit) continue;
    for (const next of [current - width, current + width, current - 1, current + 1]) {
      if (next < 0 || next >= width * height || distance(current, next) !== 1 ||
          next === blocked || walls.has(next) || costs.has(next)) continue;
      costs.set(next, cost + 1);
      queue.push(next);
    }
  }
  costs.delete(start);
  return costs;
}

export function renderBattle(parent) {
  const state = { hero: 24, enemy: 5, health: 9, enemyHealth: 7, moves: 3, action: true, guard: false };
  const section = document.createElement('section');
  section.className = 'battle';
  const intro = document.createElement('p');
  intro.textContent = 'Práctica individual. Muévete por las casillas iluminadas y usa una acción antes de terminar el turno.';
  const status = document.createElement('p');
  status.setAttribute('role', 'status');
  const grid = document.createElement('div');
  grid.className = 'battle-grid';
  const actions = document.createElement('div');
  actions.className = 'battle-actions';
  const buttons = [];
  let log = '';
  let options;

  function addAction(text, handler) {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'action';
    button.textContent = text;
    button.addEventListener('click', handler);
    actions.append(button);
    return button;
  }

  function draw() {
    const ended = state.health <= 0 || state.enemyHealth <= 0;
    options = ended ? new Map() : reachable(state.hero, state.enemy, state.moves);
    status.textContent = ended
      ? (state.enemyHealth <= 0 ? 'Victoria. La cripta queda en silencio.' : 'Derrota. Puedes volver a intentarlo.')
      : `Tu vida: ${state.health} · Guardián: ${state.enemyHealth} · Movimiento: ${state.moves} · Acción: ${state.action ? 1 : 0}. ${log}`;
    buttons.forEach((button, index) => {
      const label = index === state.hero ? 'Aventurero' : index === state.enemy ? 'Guardián' :
        walls.has(index) ? 'Pilar' : options.has(index) ? 'Mover' : 'Suelo';
      button.textContent = { Aventurero: '▲', Guardián: '◆', Pilar: '▦', Mover: '·', Suelo: '' }[label];
      button.setAttribute('aria-label', `${label}, fila ${Math.floor(index / width) + 1}, columna ${index % width + 1}`);
      button.disabled = !options.has(index);
      button.classList.toggle('reachable', options.has(index));
      button.classList.toggle('wall', walls.has(index));
    });
    attack.disabled = ended || !state.action || distance(state.hero, state.enemy) !== 1;
    defend.disabled = ended || !state.action;
    end.disabled = ended;
  }

  for (let index = 0; index < width * height; index++) {
    const button = document.createElement('button');
    button.type = 'button';
    button.addEventListener('click', () => {
      state.moves -= options.get(index);
      state.hero = index;
      draw();
      (attack.disabled ? end : attack).focus();
    });
    grid.append(button);
    buttons.push(button);
  }
  const attack = addAction('Atacar', () => {
    state.enemyHealth = Math.max(0, state.enemyHealth - 3);
    state.action = false;
    log = 'Golpeaste al guardián.';
    draw();
    (end.disabled ? restart : end).focus();
  });
  const defend = addAction('Defender', () => {
    state.guard = true;
    state.action = false;
    log = 'Reducirás el daño del próximo ataque.';
    draw();
    end.focus();
  });
  const end = addAction('Terminar turno', () => {
    const choices = [[state.enemy, 0], ...reachable(state.enemy, state.hero, 2)];
    choices.sort(([a, costA], [b, costB]) => distance(a, state.hero) - distance(b, state.hero) || costA - costB);
    state.enemy = choices[0][0];
    const adjacent = distance(state.hero, state.enemy) === 1;
    if (adjacent) state.health = Math.max(0, state.health - (state.guard ? 1 : 2));
    log = adjacent ? 'El guardián te atacó.' : 'El guardián se acercó.';
    state.moves = 3;
    state.action = true;
    state.guard = false;
    draw();
    if (end.disabled) restart.focus();
  });
  const restart = addAction('Reiniciar', () => renderBattle(parent));
  section.append(intro, status, grid, actions);
  parent.replaceChildren(section);
  draw();
}
