function node(tag, text, css) {
  const element = document.createElement(tag);
  if (text) element.textContent = text;
  if (css) element.className = css;
  return element;
}

async function request(url, method = 'GET', body) {
  const options = { method, headers: { Accept: 'application/json' } };
  if (method !== 'GET') {
    const csrf = await request('/api/csrf');
    options.headers[csrf.headerName] = csrf.token;
  }
  if (body) {
    options.headers['Content-Type'] = 'application/json';
    options.body = JSON.stringify(body);
  }
  const response = await fetch(url, options);
  if (!response.ok) {
    const problem = await response.json().catch(() => ({}));
    const fallback = { 401: 'Inicia sesión para continuar.', 403: 'Tu cuenta no tiene permiso.',
      404: 'El recurso no está disponible.', 409: 'La operación entra en conflicto con el estado de la partida.' };
    throw Object.assign(new Error(problem.detail || fallback[response.status] || 'No se pudo completar la operación.'), { status: response.status });
  }
  if (response.status !== 204 && response.headers.get('content-type')?.includes('json')) return response.json();
  return null;
}

const login = document.querySelector('#login');
const logout = document.querySelector('#logout');
let user;
const identity = request('/api/me').then(account => {
  user = account;
  login.hidden = Boolean(user);
  logout.hidden = !user;
}).catch(() => {});
logout.addEventListener('click', async () => {
  try {
    await request('/logout', 'POST');
    location.reload();
  } catch (error) {
    logout.textContent = 'Reintentar salida';
  }
});

function action(label, handler) {
  const button = node('button', label, 'action');
  button.type = 'button';
  button.addEventListener('click', handler);
  return button;
}

const date = new Intl.DateTimeFormat('es-CO', { dateStyle: 'medium', timeStyle: 'short' });
const format = session => `${date.format(new Date(session.startsAt))} · ${session.status === 'CANCELLED' ? 'Cancelada' : `${session.available} de ${session.capacity} cupos libres`}`;

function localDate(iso) {
  const value = new Date(iso);
  value.setMinutes(value.getMinutes() - value.getTimezoneOffset());
  return value.toISOString().slice(0, 16);
}

function sessionForm(parent, session, done) {
  const form = node('form', null, 'session-form');
  const fields = [
    ['title', 'Título', 'text', session?.title, 120],
    ['description', 'Descripción', 'textarea', session?.description, 2000],
    ['startsAt', 'Fecha y hora local', 'datetime-local', session ? localDate(session.startsAt) : ''],
    ['capacity', 'Cupos', 'number', session?.capacity ?? 4]
  ];
  for (const [name, label, type, value, limit] of fields) {
    const wrapper = node('label', label);
    const field = node(type === 'textarea' ? 'textarea' : 'input');
    if (type !== 'textarea') field.type = type;
    if (limit) field.maxLength = limit;
    if (type === 'number') { field.min = '1'; field.max = '20'; }
    field.name = name;
    field.value = value ?? '';
    field.required = true;
    wrapper.append(field);
    form.append(wrapper);
  }
  const submit = node('button', session ? 'Guardar cambios' : 'Publicar partida', 'action');
  const status = node('p');
  status.setAttribute('role', 'status');
  form.append(submit, status);
  form.addEventListener('submit', async event => {
    event.preventDefault();
    submit.disabled = true;
    try {
      const values = new FormData(form);
      await request(session ? `/api/sessions/${session.id}` : '/api/sessions', session ? 'PUT' : 'POST', {
        title: values.get('title'), description: values.get('description'),
        startsAt: new Date(values.get('startsAt')).toISOString(), capacity: Number(values.get('capacity')),
      });
      done();
    } catch (error) {
      status.textContent = error.message;
    } finally {
      submit.disabled = false;
    }
  });
  parent.append(form);
}

export async function renderPartidas(parent, kind, { id, page = 0, open, current }) {
  parent.replaceChildren(node('p', 'Cargando…'));
  try {
    await identity;
    if (!current()) return;
    const book = kind === 'book';
    if (book && !user) {
      const link = node('a', 'Iniciar sesión');
      link.href = login.href;
      parent.replaceChildren(node('p', 'Entra para ver tus partidas.'), link);
      return;
    }
    const mainUrl = book ? (user.role === 'ORGANIZER' ? '/api/me/sessions' : '/api/me/registrations') : '/api/sessions';
    const data = await request(id ? `/api/sessions/${id}` : `${mainUrl}?page=${page}`);
    if (!current()) return;
    parent.replaceChildren();
    const status = node('p');
    status.setAttribute('role', 'status');
    function mutate(label, url, method, done) {
      return action(label, async event => {
        event.currentTarget.disabled = true;
        const button = event.currentTarget;
        try {
          await request(url, method);
          done();
        } catch (error) {
          status.textContent = error.message;
          button.disabled = false;
        }
      });
    }
    if (id) {
      parent.append(node('h3', data.title), node('p', data.description), node('p', format(data)),
        node('p', `Organiza ${data.organizer}`));
      if (user?.role === 'PLAYER' && data.available > 0) {
        parent.append(mutate('Reservar cupo', `/api/sessions/${id}/registrations`, 'POST', () => open('book')));
      } else if (!user && data.available > 0) {
        const link = node('a', 'Entra para reservar');
        link.href = login.href;
        parent.append(link);
      }
      parent.append(status, action('Volver al tablón', () => open('board')));
      return;
    }
    if (book && user.role === 'ORGANIZER') {
      parent.append(action('Nueva partida', event => {
        event.currentTarget.remove();
        sessionForm(parent, null, () => open('book'));
      }));
    }
    const list = node('div', null, 'session-list');
    for (const session of data || []) {
      const card = node('article', null, 'listing');
      card.append(node('h3', session.title), node('p', format(session)));
      const active = session.status === 'SCHEDULED' && Date.parse(session.startsAt) > Date.now();
      if (!book) card.append(action('Ver partida', () => open('table', session.id)));
      else if (active && user.role === 'ORGANIZER') {
        card.append(action('Editar', event => {
          event.currentTarget.remove();
          sessionForm(card, session, () => open('book', null, page));
        }), mutate('Cancelar partida', `/api/sessions/${session.id}/cancel`, 'POST', () => open('book', null, page)));
      } else if (active) {
        card.append(mutate('Cancelar reserva', `/api/sessions/${session.id}/registrations/me`, 'DELETE', () => open('book', null, page)));
      }
      list.append(card);
    }
    parent.append(list);
    if (!data?.length) parent.append(node('p', book ? 'No hay partidas en esta página.' : 'No hay partidas programadas en esta página.'));
    if (page > 0) parent.append(action('Página anterior', () => open(kind, null, page - 1)));
    if (data?.length === 20) parent.append(action('Página siguiente', () => open(kind, null, page + 1)));
    parent.append(status);
  } catch (error) {
    if (!current()) return;
    if (error.status === 404 && !id && kind === 'board') {
      parent.replaceChildren(document.querySelector('#content-board').content.cloneNode(true));
      parent.querySelector('button')?.addEventListener('click', () => open('table', 'demo'));
    } else parent.replaceChildren(node('p', error.message));
  }
}
