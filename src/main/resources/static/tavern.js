import { renderPartidas } from './partidas.js';
import { renderBattle } from './battle.js';

const stops = {
  board: { label: 'Tablón de aventuras', x: -3.5, z: -3.7, height: 3.05 },
  table: { label: 'Mesa de campaña', x: 2.6, z: -.4, height: 1.7 },
  book: { label: 'Libro del aventurero', x: -4.15, z: .4, height: 1.7 },
};

const dialog = document.querySelector('#panel');
const panelTitle = document.querySelector('#panel-title');
const panelContent = document.querySelector('#panel-content');
const interact = document.querySelector('#interact');
let selectedStop;
let panelRevision = 0;

function showStop(id, sessionId, page) {
  const revision = ++panelRevision;
  panelTitle.textContent = stops[id].label;
  if (sessionId === 'demo') {
    panelContent.replaceChildren(document.querySelector('#content-table').content.cloneNode(true));
    panelContent.querySelector('button').addEventListener('click', () => showStop('board'));
  } else {
    renderPartidas(panelContent, id === 'table' && !sessionId ? 'board' : id, {
      id: sessionId, page, open: showStop, current: () => revision === panelRevision && dialog.open,
    });
  }
  if (!dialog.open) dialog.showModal();
}

document.querySelector('#practice').addEventListener('click', () => {
  panelRevision++;
  panelTitle.textContent = 'La cripta de los ecos';
  renderBattle(panelContent);
  if (!dialog.open) dialog.showModal();
});
document.querySelectorAll('[data-stop]').forEach(button => {
  button.addEventListener('click', () => showStop(button.dataset.stop));
});
interact.addEventListener('click', () => {
  if (selectedStop) showStop(selectedStop);
});

const world = document.querySelector('#world');
const fallback = document.querySelector('#fallback');
const markers = document.querySelector('#markers');

function showFallback() {
  selectedStop = undefined;
  fallback.hidden = false;
  interact.hidden = true;
  document.querySelector('#controls').hidden = true;
  markers.hidden = true;
}

async function start() {
  try {
    const { buildTavern } = await import('./tavern-scene.js');
    const tavern = buildTavern(world, markers, stops, showStop, showFallback);
    tavern.onNearStop = id => {
      selectedStop = id;
      interact.hidden = !id;
      interact.textContent = id ? `E · Abrir ${stops[id].label.toLowerCase()}` : '';
    };
    document.addEventListener('keydown', event => {
      if (dialog.open || event.altKey || event.ctrlKey || event.metaKey) return;
      if (event.code === 'KeyE' && selectedStop && !event.repeat) {
        event.preventDefault();
        showStop(selectedStop);
      }
    });
    dialog.addEventListener('close', () => tavern.clearKeys());
    new MutationObserver(() => {
      tavern.paused = dialog.open;
      tavern.clearKeys();
    }).observe(dialog, { attributes: true, attributeFilter: ['open'] });
  } catch (error) {
    console.warn('No se pudo iniciar la escena 3D.', error);
    showFallback();
  }
}

start();
