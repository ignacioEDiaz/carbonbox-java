// Frontend sin dependencias: rutas por hash y fetch a la API.
const app = document.getElementById("app");
const ESTADOS = { PENDING: "Pendiente", PROCESSING: "Procesando", COMPLETED: "Completado", FAILED: "Falló" };
let temporizador = null; // polling del detalle

const esc = (v) => String(v ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);
const fecha = (iso) => (iso ? new Date(iso).toLocaleString("es-AR", { dateStyle: "short", timeStyle: "short", hour12: false }) : "—");
const badge = (s) => `<span class="badge ${esc(s)}">${esc(ESTADOS[s] || s)}</span>`;

async function api(ruta, opciones) {
  const r = await fetch("/api" + ruta, opciones);
  const cuerpo = await r.json().catch(() => ({}));
  if (!r.ok) throw new Error(cuerpo.message || `Error ${r.status}`);
  return cuerpo;
}

function rutear() {
  clearTimeout(temporizador);
  const h = location.hash.replace(/^#/, "") || "/";
  const detalle = h.match(/^\/uploads\/([0-9a-f-]{36})$/i);
  if (h === "/uploads/new") return pantallaSubir();
  if (detalle) return pantallaDetalle(detalle[1], 1);
  if (h.startsWith("/uploads")) return pantallaLista(Number(new URLSearchParams(h.split("?")[1]).get("page")) || 1);
  return pantallaTablero();
}

async function pantallaTablero() {
  app.innerHTML = "<h1>Tablero</h1><p class='suave'>Cargando…</p>";
  try {
    const [r, meses, cola] = await Promise.all([api("/uploads/summary"), api("/activity/by-month"), api("/queue/stats")]);
    app.innerHTML = `
      <h1>Tablero</h1>
      <div class="tarjetas">
        ${tarjeta(r.total, "Uploads")}${tarjeta(r.completed, "Completados")}
        ${tarjeta(r.pending + r.processing, "En proceso")}${tarjeta(r.failed, "Fallidos")}
      </div>
      <p><a class="boton" href="#/uploads/new">Subir un CSV</a></p>
      <h2>Actividad por mes</h2>
      ${meses.length ? `<div class="envoltura"><table class="tabla">
        <tr><th>Mes</th><th>Categoría</th><th class="num">Total</th><th>Unidad</th><th class="num">Filas</th></tr>
        ${meses.map((m) => `<tr><td>${esc(m.month)}</td><td>${esc(m.category)}</td><td class="num">${esc(m.total)}</td><td>${esc(m.unit)}</td><td class="num">${esc(m.rows)}</td></tr>`).join("")}
      </table></div>` : "<p class='suave'>Todavía no hay datos procesados.</p>"}
      <h2>Cola</h2>
      <p class="suave">En cola: ${cola.queued} · tomados por el worker: ${cola.in_flight} · procesados: ${cola.done} · en la DLQ: ${cola.dead}</p>`;
  } catch (e) {
    app.innerHTML = `<h1>Tablero</h1><div class="aviso error">${esc(e.message)}</div>`;
  }
}
const tarjeta = (n, t) => `<div class="tarjeta"><div class="n">${esc(n)}</div><div class="t">${esc(t)}</div></div>`;

async function pantallaLista(pagina) {
  app.innerHTML = "<h1>Uploads</h1><p class='suave'>Cargando…</p>";
  try {
    const p = await api(`/uploads?page=${pagina}&size=10`);
    app.innerHTML = `
      <h1>Uploads</h1>
      ${p.items.length ? `<div class="envoltura"><table class="tabla">
        <tr><th>Archivo</th><th>Estado</th><th>Subido</th><th class="num">Filas procesadas</th><th class="num">Con error</th><th></th></tr>
        ${p.items.map((u) => `<tr><td>${esc(u.fileName)}</td><td>${badge(u.status)}</td><td>${fecha(u.uploadedAt)}</td>
          <td class="num">${u.processedRows}</td><td class="num">${u.errorRows}</td><td><a href="#/uploads/${u.id}">Ver</a></td></tr>`).join("")}
      </table></div>
      <div class="paginas">
        <button class="secundario" ${pagina <= 1 ? "disabled" : ""} onclick="location.hash='#/uploads?page=${pagina - 1}'">Anterior</button>
        Página ${p.page} de ${Math.max(p.totalPages, 1)}
        <button class="secundario" ${pagina >= p.totalPages ? "disabled" : ""} onclick="location.hash='#/uploads?page=${pagina + 1}'">Siguiente</button>
      </div>` : `<p class="suave">No hay uploads. <a href="#/uploads/new">Subí el primero</a>.</p>`}`;
  } catch (e) {
    app.innerHTML = `<h1>Uploads</h1><div class="aviso error">${esc(e.message)}</div>`;
  }
}

function pantallaSubir() {
  app.innerHTML = `
    <h1>Subir CSV</h1>
    <p class="suave">Columnas: <code>category,amount,unit,date</code> · fecha <code>YYYY-MM-DD</code> · hasta 5 MB.</p>
    <label class="zona" id="zona" for="archivo">Arrastrá el archivo acá o hacé clic para elegirlo
      <input type="file" id="archivo" accept=".csv,text/csv" hidden></label>
    <div class="progreso" id="barra" hidden><div></div></div>
    <div id="mensaje"></div>`;
  const zona = document.getElementById("zona");
  const input = document.getElementById("archivo");
  input.addEventListener("change", () => input.files[0] && subir(input.files[0]));
  zona.addEventListener("dragover", (e) => { e.preventDefault(); zona.classList.add("encima"); });
  zona.addEventListener("dragleave", () => zona.classList.remove("encima"));
  zona.addEventListener("drop", (e) => {
    e.preventDefault();
    zona.classList.remove("encima");
    if (e.dataTransfer.files[0]) subir(e.dataTransfer.files[0]);
  });
}

function subir(archivo) {
  const mensaje = document.getElementById("mensaje");
  if (!archivo.name.toLowerCase().endsWith(".csv")) {
    mensaje.innerHTML = `<div class="aviso error">Solo se aceptan archivos .csv</div>`;
    return;
  }
  const barra = document.getElementById("barra");
  barra.hidden = false;
  mensaje.innerHTML = "";
  // XMLHttpRequest en vez de fetch para poder mostrar el progreso de la subida
  const xhr = new XMLHttpRequest();
  xhr.open("POST", "/api/uploads");
  xhr.upload.onprogress = (e) => { if (e.lengthComputable) barra.firstElementChild.style.width = `${(100 * e.loaded) / e.total}%`; };
  xhr.onload = () => {
    let cuerpo = {};
    try { cuerpo = JSON.parse(xhr.responseText); } catch { /* respuesta vacia */ }
    if (xhr.status === 200 || xhr.status === 202) {
      location.hash = `#/uploads/${cuerpo.id}`;
    } else {
      barra.hidden = true;
      mensaje.innerHTML = `<div class="aviso error">${esc(cuerpo.message || "No se pudo subir el archivo")}</div>`;
    }
  };
  xhr.onerror = () => { barra.hidden = true; mensaje.innerHTML = `<div class="aviso error">No hay conexión con el servidor</div>`; };
  const datos = new FormData();
  datos.append("file", archivo);
  xhr.send(datos);
}

async function pantallaDetalle(id, pagina) {
  try {
    const u = await api(`/uploads/${id}`);
    const enCurso = u.status === "PENDING" || u.status === "PROCESSING";
    let resultados = "";
    if (u.status === "COMPLETED") {
      const [filas, errores] = await Promise.all([api(`/uploads/${id}/results?page=${pagina}&size=50`), api(`/uploads/${id}/errors`)]);
      resultados = tablaResultados(id, filas) + tablaErrores(errores);
    } else if (u.status === "FAILED") {
      const errores = await api(`/uploads/${id}/errors`);
      resultados = `<div class="aviso error"><strong>No se pudo procesar.</strong> ${esc(u.failureReason)}</div>
        <button class="secundario" id="reintentar">Volver a encolar</button>` + tablaErrores(errores);
    }
    app.innerHTML = `
      <p><a href="#/uploads">← Volver a la lista</a></p>
      <h1>${esc(u.fileName)}</h1>
      <dl class="datos">
        <dt>Estado</dt><dd>${badge(u.status)}</dd>
        <dt>Subido</dt><dd>${fecha(u.uploadedAt)}</dd>
        <dt>Actualizado</dt><dd>${fecha(u.updatedAt)}</dd>
        <dt>Filas procesadas</dt><dd>${u.processedRows}</dd>
        <dt>Filas con error</dt><dd>${u.errorRows}</dd>
      </dl>
      ${enCurso ? `<div class="progreso indeterminado"><div></div></div><p class="suave">${u.status === "PENDING" ? "Esperando en la cola…" : "Procesando…"} (se actualiza cada 3 segundos)</p>` : ""}
      ${resultados}`;
    const boton = document.getElementById("reintentar");
    if (boton) boton.onclick = async () => {
      boton.disabled = true;
      try { await api(`/uploads/${id}/redrive`, { method: "POST" }); pantallaDetalle(id, 1); }
      catch (e) { boton.disabled = false; alert(e.message); }
    };
    if (enCurso) temporizador = setTimeout(() => pantallaDetalle(id, pagina), 3000);
  } catch (e) {
    app.innerHTML = `<p><a href="#/uploads">← Volver a la lista</a></p><div class="aviso error">${esc(e.message)}</div>`;
  }
}

function tablaResultados(id, p) {
  if (!p.items.length) return "";
  return `<h2>Datos procesados (${p.totalItems})</h2>
    <div class="envoltura"><table class="tabla">
      <tr><th class="num">Fila</th><th>Categoría</th><th class="num">Cantidad</th><th>Unidad</th><th>Fecha</th></tr>
      ${p.items.map((r) => `<tr><td class="num">${r.rowNumber}</td><td>${esc(r.category)}</td><td class="num">${esc(r.amount)}</td><td>${esc(r.unit)}</td><td>${esc(r.date)}</td></tr>`).join("")}
    </table></div>
    ${p.totalPages > 1 ? `<div class="paginas">
      <button class="secundario" ${p.page <= 1 ? "disabled" : ""} onclick="pantallaDetalle('${id}', ${p.page - 1})">Anterior</button>
      Página ${p.page} de ${p.totalPages}
      <button class="secundario" ${p.page >= p.totalPages ? "disabled" : ""} onclick="pantallaDetalle('${id}', ${p.page + 1})">Siguiente</button></div>` : ""}`;
}

function tablaErrores(errores) {
  if (!errores.length) return "";
  return `<h2>Filas con error (${errores.length})</h2>
    <div class="envoltura"><table class="tabla">
      <tr><th class="num">Fila</th><th>Motivo</th><th>Contenido</th></tr>
      ${errores.map((e) => `<tr><td class="num">${e.rowNumber}</td><td>${esc(e.message)}</td><td><code>${esc(e.rawLine)}</code></td></tr>`).join("")}
    </table></div>`;
}

window.addEventListener("hashchange", rutear);
rutear();
