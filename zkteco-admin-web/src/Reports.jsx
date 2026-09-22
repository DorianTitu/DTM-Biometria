import { useEffect, useMemo, useState } from 'react';
import './reports.css';

const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Guayaquil', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());
const startDate = (() => { const d = new Date(`${today}T12:00:00`); d.setDate(d.getDate() - 13); return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Guayaquil', year: 'numeric', month: '2-digit', day: '2-digit' }).format(d); })();
const labels = { PRESENT: 'Puntual', LATE: 'Atraso', ABSENT: 'Ausente' };
const dateLabel = value => new Intl.DateTimeFormat('es-EC', { timeZone: 'UTC', day: '2-digit', month: 'short' }).format(new Date(`${value}T12:00:00Z`));

export default function Reports({ token, api, user, logout, onBack }) {
  const [options, setOptions] = useState({ courses: [], parallels: [] });
  const [filters, setFilters] = useState({ from: startDate, to: today, course: '', parallel: '', state: '', search: '' });
  const [report, setReport] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [downloading, setDownloading] = useState(false);
  const query = useMemo(() => new URLSearchParams(Object.entries(filters).filter(([, value]) => value)), [filters]);
  const auth = { Authorization: `Bearer ${token}` };

  useEffect(() => {
    fetch(`${api}/api/reports/options`, { headers: auth }).then(r => { if (!r.ok) throw new Error(); return r.json(); })
      .then(setOptions).catch(() => setError('No se pudieron cargar los filtros.')).finally(() => setLoading(false));
  }, [api, token]);
  useEffect(() => {
    if (!filters.from || !filters.to || filters.from > filters.to) { setError('La fecha inicial debe ser anterior o igual a la fecha final.'); setReport(null); setLoading(false); return; }
    setLoading(true); setError('');
    const timer = window.setTimeout(() => fetch(`${api}/api/reports/attendance?${query}`, { headers: auth })
      .then(async r => { if (!r.ok) { const body = await r.json().catch(() => ({})); throw new Error(body.message || 'No se pudo cargar la reportería.'); } return r.json(); })
      .then(setReport).catch(e => setError(e.message)).finally(() => setLoading(false)), 180);
    return () => window.clearTimeout(timer);
  }, [api, token, query]);

  const daily = useMemo(() => {
    if (!report) return [];
    const days = new Map();
    for (const row of report.rows) {
      if (!days.has(row.date)) days.set(row.date, { date: row.date, present: 0, late: 0, absent: 0 });
      const d = days.get(row.date); d[row.state === 'PRESENT' ? 'present' : row.state === 'LATE' ? 'late' : 'absent']++;
    }
    return [...days.values()].slice(-14);
  }, [report]);
  const maxDaily = Math.max(1, ...daily.map(d => d.present + d.late + d.absent));

  async function downloadCsv() {
    setDownloading(true);
    try {
      const r = await fetch(`${api}/api/reports/attendance.csv?${query}`, { headers: auth });
      if (!r.ok) throw new Error('No se pudo descargar el archivo.');
      const blob = await r.blob(); const url = URL.createObjectURL(blob); const a = document.createElement('a');
      a.href = url; a.download = 'asistencia-demo.csv'; a.click(); URL.revokeObjectURL(url);
    } catch (e) { setError(e.message); } finally { setDownloading(false); }
  }
  function set(key, value) { setFilters(old => ({ ...old, [key]: value })); }

  return <div className="app-shell">
    <aside className="sidebar"><div className="brand"><span className="brand-mark">B</span><span>Biometric</span></div><p className="nav-caption">REPORTERÍA</p><button className="nav-item active">▤ Asistencia</button><div className="sidebar-bottom"><div className="profile"><span className="avatar">{user.username.slice(0,1).toUpperCase()}</span><div><strong>{user.username}</strong><small>{user.role === 'ADMINISTRATOR' ? 'Administrador' : 'Inspector'}</small></div></div><button className="logout" onClick={logout}>↪ Cerrar sesión</button></div></aside>
    <main className="workspace report-workspace"><header className="topbar report-topbar"><div><p className="eyebrow">BIOMETRIC / REPORTES</p><h1>Reporte de asistencia</h1><p className="report-subtitle">Consulta marcaciones, atrasos y ausencias por período y grupo.</p></div><div className="report-actions"><span className="demo-tag"><i/> Datos de demostración</span>{user.role === 'ADMINISTRATOR' && <button className="secondary" onClick={onBack}>Gestión académica</button>}<button className="primary" onClick={downloadCsv} disabled={!report || loading || downloading}>⇩ {downloading ? 'Preparando…' : 'Exportar CSV'}</button></div></header>
      <section className="report-filters"><label>Desde<input type="date" value={filters.from} max={filters.to || today} onChange={e => set('from', e.target.value)}/></label><label>Hasta<input type="date" value={filters.to} min={filters.from} max={today} onChange={e => set('to', e.target.value)}/></label><label>Curso<select value={filters.course} onChange={e => set('course', e.target.value)}><option value="">Todos los cursos</option>{options.courses.map(x=><option key={x}>{x}</option>)}</select></label><label>Paralelo<select value={filters.parallel} onChange={e => set('parallel', e.target.value)}><option value="">Todos</option>{options.parallels.map(x=><option key={x}>{x}</option>)}</select></label><label>Estado<select value={filters.state} onChange={e => set('state', e.target.value)}><option value="">Todos los estados</option><option value="PRESENT">Puntual</option><option value="LATE">Atraso</option><option value="ABSENT">Ausente</option></select></label><label className="report-search">Buscar<input value={filters.search} onChange={e => set('search', e.target.value)} placeholder="Estudiante o ID biométrico"/></label></section>
      {error && <div className="report-error" role="alert">{error}</div>}
      <section className="report-metrics"><Metric title="Marcaciones" value={report?.summary.total} tone="blue" loading={loading}/><Metric title="Puntuales" value={report?.summary.present} tone="green" loading={loading}/><Metric title="Atrasos" value={report?.summary.late} tone="amber" loading={loading}/><Metric title="Ausencias" value={report?.summary.absent} tone="red" loading={loading}/></section>
      <section className="report-grid"><div className="card report-chart"><div className="report-card-heading"><div><h2>Asistencia por día</h2><p>Marcaciones dentro del período seleccionado</p></div><span className="chart-legend"><i/> Registros</span></div>{loading ? <div className="report-loading">Actualizando datos…</div> : daily.length ? <div className="bars" aria-label="Gráfico de marcaciones por día">{daily.map(d=>{const total=d.present+d.late+d.absent; return <div className="bar-column" key={d.date}><span className="bar-value">{total}</span><div className="bar-track"><i style={{height:`${Math.max(5,total/maxDaily*100)}%`}}/></div><small>{dateLabel(d.date)}</small></div>})}</div> : <div className="report-loading">Sin datos para mostrar.</div>}</div><div className="card report-breakdown"><div className="report-card-heading"><div><h2>Resumen del período</h2><p>Distribución de registros</p></div></div>{report && <div className="breakdown-content"><Breakdown label="Puntuales" value={report.summary.present} total={report.summary.total} tone="green"/><Breakdown label="Atrasos" value={report.summary.late} total={report.summary.total} tone="amber"/><Breakdown label="Ausencias" value={report.summary.absent} total={report.summary.total} tone="red"/><p className="timezone-note">Horario local · Ecuador (UTC−5)</p></div>}</div></section>
      <section className="card report-table-card"><div className="report-card-heading table-heading"><div><h2>Detalle de asistencia</h2><p>{report?.rows.length ?? 0} registros · ordenados por fecha</p></div><span className="demo-note">Los datos son ficticios para la revisión del sistema.</span></div><div className="table-wrap"><table><thead><tr><th>Fecha</th><th>Estudiante</th><th>ID biométrico</th><th>Curso</th><th>Paralelo</th><th>Estado</th><th>Hora</th></tr></thead><tbody>{loading ? <tr><td colSpan="7" className="report-empty">Cargando reporte…</td></tr> : report?.rows.length ? [...report.rows].reverse().map(row=><tr key={row.id}><td>{dateLabel(row.date)}</td><td className="student-name">{row.student}</td><td>{row.biometricId}</td><td>{row.course}</td><td>{row.parallel}</td><td><span className={`attendance-state ${row.state.toLowerCase()}`}><i/>{labels[row.state]}</span></td><td>{row.arrival || '—'}</td></tr>) : <tr><td colSpan="7" className="report-empty">No hay marcaciones que coincidan con los filtros.</td></tr>}</tbody></table></div></section>
      <footer className="report-footer">Reporte generado con zona horaria de Ecuador · Los datos de esta versión son de demostración.</footer>
    </main>
  </div>;
}
function Metric({ title, value, tone, loading }) { return <article className="metric-card"><div className={`metric-icon ${tone}`}>{tone==='blue'?'▤':tone==='green'?'✓':tone==='amber'?'◷':'!'}</div><div><small>{title}</small><strong>{loading?'—':Number(value||0).toLocaleString('es-EC')}</strong><span>en el período</span></div></article> }
function Breakdown({ label, value, total, tone }) { const percent=total?Math.round(value/total*100):0; return <div className="breakdown-row"><div><span>{label}</span><strong>{value} <small>{percent}%</small></strong></div><div className="progress-track"><i className={tone} style={{width:`${percent}%`}}/></div></div> }
