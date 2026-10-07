'use strict';
(() => {
const $ = id => document.getElementById(id);
const API = '/access/api';
const state = { session: null, users: [], product: 'android', channel: '', selected: null, poll: null, expires: 0, request: 0, owner: null };
const channels = ['dev', 'alfa', 'beta', 'rc'];
const products = { android: 'Telegram Android', react: 'Telegram React' };
const code = new URL(location.href).searchParams.get('code');
const next = new URL(location.href).searchParams.get('next');
function show(id) { for (const name of ['loading','login','pairing','forbidden','dashboard']) $(name).hidden = name !== id; }
function notice(message, error = false) { $('notice').textContent = message; $('notice').classList.toggle('error', error); $('notice').hidden = false; }
async function api(path, options = {}) {
 const headers = { Accept:'application/json', ...options.headers };
 if (options.body) headers['Content-Type'] = 'application/json';
 if (options.method && options.method !== 'GET' && state.session?.csrf_token) headers['X-CSRF-Token'] = state.session.csrf_token;
 const controller = new AbortController(); const timeout = setTimeout(() => controller.abort(), 15000);
 let response;
 try { response = await fetch(API + path, { credentials:'same-origin', cache:'no-store', ...options, headers, signal:controller.signal }); }
 catch { throw new Error('No se puede conectar. Comprueba la conexión y vuelve a intentarlo.'); }
 finally { clearTimeout(timeout); }
 if (response.status === 401) { state.session = null; state.users = []; $('profile-controls').hidden = true; $('country-summary').replaceChildren(); $('users-body').replaceChildren(); if ($('editor').open) $('editor').close(); show('login'); const error = new Error('La sesión ha caducado. Vuelve a identificarte.'); error.status = 401; throw error; }
 if (!response.ok) throw new Error(response.status === 403 ? 'Tu cuenta no tiene permiso para esta acción.' : response.status === 429 ? 'Demasiadas solicitudes. Espera un momento antes de reintentar.' : 'No se ha podido completar la operación. Inténtalo de nuevo.');
 return response.status === 204 ? {} : response.json();
}
function element(tag, text, cls) { const el = document.createElement(tag); if (text !== undefined) el.textContent = text; if (cls) el.className = cls; return el; }
function displayName(user) { return user.first_name || user.username || `Usuario ${user.id}`; }
function seen(value) { if (!value) return 'Sin actividad'; const parsed = typeof value === 'number' ? value * 1000 : Date.parse(value); return Number.isFinite(parsed) ? new Date(parsed).toLocaleString('es-ES', {dateStyle:'short',timeStyle:'short'}) : 'Sin actividad'; }
function relevantSessions(user) { return (user.sessions || []).filter(s => s.product === state.product && (!state.channel || s.channel === state.channel)); }
function render() {
 const query = $('search').value.trim().toLocaleLowerCase();
 const filtered = state.users.filter(u => `${u.first_name || ''} ${u.username || ''} ${u.id}`.toLocaleLowerCase().includes(query));
 $('stat-users').textContent = state.users.length;
 $('stat-active').textContent = state.users.reduce((count, u) => count + relevantSessions(u).filter(s => s.active).length, 0);
 $('stat-granted').textContent = state.users.filter(u => (String(u.id) === state.owner && state.channel !== 'stable') || (u.grants?.[state.product] || []).some(c => channels.includes(c) && (!state.channel || c === state.channel))).length;
 $('grant-caption').textContent = state.channel ? `Autorizados en ${state.channel}` : 'Autorizados en esta aplicación';
 const body = $('users-body'); body.replaceChildren();
 for (const user of filtered) {
  const tr = element('tr'); const personCell = element('td'); const person = element('div',undefined,'person');
  person.append(element('span',displayName(user).slice(0,1).toUpperCase(),'avatar'));
  const name = element('div',displayName(user)); name.append(element('small',user.username ? `@${user.username} · ${user.id}` : String(user.id))); person.append(name); personCell.append(person); tr.append(personCell);
  const grants = element('td'); if (String(user.id) === state.owner) grants.append(element('span','Administrador','badge')); for (const branch of user.grants?.[state.product] || []) grants.append(element('span',branch,'badge')); if (!grants.children.length) grants.append(element('span','Solo stable','activity')); tr.append(grants);
  const sessions = relevantSessions(user); const active = sessions.some(s => s.active); const lastSeen = Math.max(0, ...sessions.map(s => s.last_seen || 0)); const activity = element('td',active ? '● Sesión reciente' : seen(lastSeen),`activity ${active ? 'online' : ''}`); tr.append(activity);
  const action = element('td'); const edit = element('button','Gestionar','secondary'); edit.type = 'button'; edit.setAttribute('aria-label',`Gestionar permisos de ${displayName(user)}`); edit.addEventListener('click',() => openEditor(user)); action.append(edit); tr.append(action); body.append(tr);
 }
 $('empty').hidden = filtered.length !== 0;
 $('empty').querySelector('h3').textContent = state.users.length ? 'No hay coincidencias' : 'Aún no hay usuarios en esta vista';
 $('empty').querySelector('p').textContent = state.users.length ? 'Prueba otro nombre, @usuario o ID.' : 'Aparecerán cuando se identifiquen con Moonbot y soliciten acceso.';
 $('result-count').textContent = `${filtered.length} ${filtered.length === 1 ? 'persona' : 'personas'} · ${state.channel || 'Todas las ramas'}`;
}
async function loadUsers() {
 const request = ++state.request; $('refresh').disabled = true;
 $('result-count').textContent = 'Actualizando usuarios…';
 try { const result = await api(`/admin/users?product=${state.product}&channel=${state.channel}`); if (request !== state.request) return; state.users = result.users || []; state.owner = String(result.owner_id || ''); $('active-window').textContent = `Comprobación recibida en los últimos ${Math.round((result.active_window || 180)/60)} min`; render(); }
 catch (error) { if (request === state.request) { state.users = []; render(); $('result-count').textContent = 'No se han podido cargar los usuarios'; notice(error.message,true); } }
 finally { if (request === state.request) $('refresh').disabled = false; }
}
async function signedIn(session) {
 state.session = session; clearTimeout(state.poll); $('notice').hidden = true;
 loadProfile().catch(error => notice(error.message,true));
 if (code) { $('pair-code').textContent = code; $('approve').disabled = true; show('pairing'); try { const device = await api('/device/info?code=' + encodeURIComponent(code)); $('pairing').querySelector('h1').textContent = `Autoriza ${products[device.product] || 'la aplicación'}`; $('pairing').querySelector('p').textContent = `Rama ${device.channel}. Confirma únicamente si has iniciado esta solicitud desde tu aplicación.`; $('approve').disabled = false; } catch (error) { $('pair-result').textContent = error.message; } return; }
 if (next === '/') { location.replace('/'); return; }
 if (!session.is_admin) { show('forbidden'); return; }
 $('admin-name').textContent = displayName(session.user); show('dashboard'); await loadUsers();
}
async function startLogin() {
 clearTimeout(state.poll); $('login-start').disabled = true; $('login-retry').disabled = true;
 try { const result = await api('/auth/start'); if (!/^[a-f0-9]{24}$/i.test(result.user_code || '')) throw new Error('La solicitud no incluye un código de verificación válido.'); $('login-code').textContent = result.user_code; const target = new URL(result.verification_uri); if (target.protocol !== 'https:' || target.hostname !== 't.me') throw new Error('El enlace de autenticación no es válido.'); $('bot-link').href = target.href; $('login-pending').hidden = false; $('login-start').hidden = true; state.expires = Date.now() + Math.min(result.expires_in || 300,300) * 1000; poll(result.login_token); }
 catch (error) { notice(error.message,true); }
 finally { $('login-start').disabled = false; $('login-retry').disabled = false; }
}
async function poll(token) {
 if (Date.now() >= state.expires) { notice('La solicitud ha caducado. Crea otra para continuar.',true); return; }
 try { const result = await api('/auth/poll',{method:'POST',body:JSON.stringify({login_token:token})}); if (result.user) { await signedIn(result); return; } }
 catch (error) { notice(error.message,true); return; }
 state.poll = setTimeout(() => poll(token),2500);
}
function openEditor(user) {
 state.selected = user; const isOwner = String(user.id) === state.owner; $('save').disabled = isOwner; $('editor-name').textContent = displayName(user); $('editor-identity').textContent = `${user.username ? '@'+user.username+' · ' : ''}${user.id}`; $('editor-product').textContent = products[state.product] + (isOwner ? ' · El administrador conserva acceso a todas las ramas.' : ''); $('editor-error').textContent = '';
 const options = $('grant-options'); for (const old of options.querySelectorAll('label')) old.remove();
 for (const channel of channels) { const label = element('label',undefined,'grant-choice'); const input = document.createElement('input'); input.type = 'checkbox'; input.name = 'channel'; input.value = channel; input.checked = isOwner || (user.grants?.[state.product] || []).includes(channel); input.disabled = isOwner; label.append(input,element('span',channel),element('small','Acceso privado')); options.append(label); }
 const sessions = $('editor-sessions'); sessions.replaceChildren();
 for (const session of (user.sessions || []).filter(s => s.product === state.product)) {
  const row = element('div',undefined,'session-row'); const text = element('div',`${session.channel} · ${session.version || 'Versión no informada'}`); text.append(element('small',`${session.active ? 'Comprobación reciente · ' : ''}${seen(session.last_seen)}`)); const revoke = element('button','Revocar','secondary'); revoke.type = 'button'; revoke.addEventListener('click',async () => { revoke.disabled = true; try { await api('/admin/revoke',{method:'POST',body:JSON.stringify({session_id:session.id})}); row.remove(); notice('Sesión revocada.'); await loadUsers(); } catch (error) { $('editor-error').textContent = error.message; revoke.disabled = false; } }); row.append(text,revoke); sessions.append(row);
 }
 if (!sessions.children.length) sessions.append(element('p','Sin sesiones registradas.','hint')); $('editor').showModal();
}
$('grant-form').addEventListener('submit',async event => { event.preventDefault(); $('save').disabled = true; $('editor-error').textContent = ''; try { const allowed = [...$('grant-options').querySelectorAll('input:checked')].map(input => input.value); await api('/admin/grants',{method:'PUT',body:JSON.stringify({user_id:state.selected.id,product:state.product,channels:allowed})}); $('editor').close(); notice('Permisos actualizados.'); await loadUsers(); } catch (error) { $('editor-error').textContent = error.message; } finally { $('save').disabled = false; } });
for (const id of ['editor-close','editor-cancel']) $(id).addEventListener('click',() => $('editor').close());
$('approve').addEventListener('click',async () => { $('approve').disabled = true; try { await api('/device/approve',{method:'POST',body:JSON.stringify({code})}); $('pair-result').textContent = 'Dispositivo autorizado. Ya puedes volver a la aplicación.'; } catch (error) { $('pair-result').textContent = error.message; $('approve').disabled = false; } });
for (const button of document.querySelectorAll('.logout')) button.addEventListener('click',async () => { try { await api('/auth/logout',{method:'POST',body:'{}'}); location.replace('/access/'); } catch (error) { notice(error.message,true); } });
for (const button of document.querySelectorAll('[data-product]')) button.addEventListener('click',() => { state.product = button.dataset.product; for (const other of document.querySelectorAll('[data-product]')) { const selected = other === button; other.classList.toggle('active',selected); other.setAttribute('aria-pressed',String(selected)); } loadUsers(); });
for (const button of document.querySelectorAll('[data-channel]')) button.addEventListener('click',() => { state.channel = button.dataset.channel; for (const other of document.querySelectorAll('[data-channel]')) { const selected = other === button; other.classList.toggle('active',selected); other.setAttribute('aria-pressed',String(selected)); } loadUsers(); });
$('login-start').addEventListener('click',startLogin); $('login-retry').addEventListener('click',startLogin); $('refresh').addEventListener('click',loadUsers); $('search').addEventListener('input',render);
async function loadProfile() {
 const profile = await api('/profile'); $('profile-controls').hidden = false;
 const select = $('country-choice'); select.replaceChildren(element('option','No indicar país')); select.firstChild.value = '';
 const names = typeof Intl.DisplayNames === 'function' ? new Intl.DisplayNames(['es'],{type:'region'}) : null;
 for (const country of [...profile.countries].sort((a,b) => (names?.of(a) || a).localeCompare(names?.of(b) || b,'es'))) { const option = element('option',names?.of(country) || country); option.value = country; select.append(option); }
 select.value = profile.country || ''; $('country-consent').checked = !!profile.country;
 if (state.session?.is_admin) { const stats = await api('/admin/countries'); const area = $('country-summary'); area.replaceChildren(element('h3','Países aportados voluntariamente')); for (const entry of stats.countries) area.append(element('p',`${names?.of(entry.country) || entry.country}: ${entry.count}`)); area.append(element('p',`Grupos con menos de ${stats.minimum_group} participantes: país oculto (${stats.suppressed} aportaciones).`)); area.hidden = false; }
}
$('country-save').addEventListener('click',async () => { const country = $('country-choice').value; if (country && !$('country-consent').checked) { $('country-result').textContent = 'Marca la casilla solo si deseas aportar el país. También puedes elegir «No indicar país».'; return; } $('country-save').disabled = true; try { await api('/profile/country',{method:'POST',body:JSON.stringify({country:country || null,consent:!!country && $('country-consent').checked})}); $('country-result').textContent = country ? 'Elección guardada. Puedes retirarla cuando quieras.' : 'País retirado de las estadísticas.'; await loadProfile(); } catch (error) { $('country-result').textContent = error.message; } finally { $('country-save').disabled = false; } });
$('profile-delete').addEventListener('click',async () => { if (!window.confirm('¿Eliminar tu perfil de acceso de Todosobrealltech, sus permisos y sesiones? No se elimina tu cuenta de Telegram.')) return; $('profile-delete').disabled = true; try { await api('/profile/delete',{method:'POST',body:JSON.stringify({confirm:'delete_access_profile'})}); location.replace('/access/'); } catch (error) { notice(error.message,true); $('profile-delete').disabled = false; } });
api('/auth/session').then(signedIn).catch(error => { show('login'); if (error.status !== 401) notice(error.message,true); });
setInterval(() => { if (state.session?.is_admin && !$('dashboard').hidden && !$('editor').open && !document.hidden) loadUsers(); },30000);
})();
