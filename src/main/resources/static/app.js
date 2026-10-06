(() => {
  const $ = (id) => document.getElementById(id);
  const state = { grades: [], courses: [], inputs: new Map() };

  // Build DOM nodes without innerHTML so names and other user text can never inject markup.
  function el(tag, props = {}, ...children) {
    const node = document.createElement(tag);
    Object.assign(node, props);
    for (const c of children) node.append(c);
    return node;
  }

  async function api(path, options) {
    const res = await fetch(path, options);
    let body = null;
    try { body = await res.json(); } catch (_) { /* non-JSON error */ }
    if (!res.ok) throw new Error((body && body.error) || `Request failed (${res.status})`);
    return body;
  }

  const mode = () => document.querySelector('input[name="mode"]:checked').value;
  const setStatus = (msg) => { $('status').textContent = msg || ''; };
  const fmt = (n) => (n == null ? '-' : Number(n).toFixed(2));

  // ---------- calculator ----------
  async function loadCourses() {
    const sem = $('semester').value;
    setStatus('');
    state.inputs.clear();
    try {
      state.courses = await api(`/api/courses?semester=${encodeURIComponent(sem)}`);
    } catch (e) {
      setStatus(e.message);
      return;
    }
    renderCourses();
  }

  function renderCourses() {
    const list = $('courses');
    list.replaceChildren();
    const marks = mode() === 'marks';
    $('entry-head').textContent = marks ? 'Marks (0 to 100)' : 'Grade';
    if (!state.courses.length) {
      list.append(el('li', {}, el('span', { textContent: 'No courses are set up for this semester.' })));
      return;
    }
    for (const c of state.courses) {
      let input;
      if (marks) {
        input = el('input', { type: 'text', inputMode: 'decimal', autocomplete: 'off' });
      } else {
        input = el('select');
        input.append(el('option', { value: '', textContent: 'Select' }));
        for (const g of state.grades) {
          input.append(el('option', { value: g.grade, textContent: `${g.grade} (${g.points})` }));
        }
      }
      input.setAttribute('aria-label', `${marks ? 'Marks' : 'Grade'} for ${c.title}`);
      state.inputs.set(c.code, input);
      list.append(el('li', {},
        el('span', { className: 'course-title' }, c.title, el('span', { className: 'course-code', textContent: c.code })),
        el('span', { className: 'num', textContent: String(c.credits) }),
        input));
    }
  }

  async function calculate() {
    setStatus('');
    const entries = state.courses.map((c) => ({ code: c.code, value: state.inputs.get(c.code).value }));
    const btn = $('calculate');
    btn.disabled = true;
    try {
      const data = await api('/api/calculate', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          name: $('name').value, roll: $('roll').value,
          semester: Number($('semester').value), mode: mode(), entries,
        }),
      });
      renderResult(data);
    } catch (e) {
      setStatus(e.message);
    } finally {
      btn.disabled = false;
    }
  }

  function renderResult(d) {
    const rows = d.semesters.map((s) => el('tr', { className: s.semester === d.semester ? 'now' : '' },
      el('th', { scope: 'row', textContent: `Semester ${s.semester}` }),
      el('td', { textContent: `${s.credits} credits` }),
      el('td', { textContent: fmt(s.sgpa) })));
    const counted = `${d.items.length} of ${state.courses.length} courses counted`;
    $('result').replaceChildren(
      el('p', { className: 'who' }, el('strong', { textContent: d.name }),
        el('span', { textContent: `${d.roll}, semester ${d.semester}` })),
      el('p', { className: 'score', textContent: fmt(d.sgpa) }),
      el('p', { className: 'score-label', textContent: `SGPA from ${d.semesterCredits} credits (${counted})` }),
      el('p', { className: 'score cgpa', textContent: fmt(d.cgpa) }),
      el('p', { className: 'score-label', textContent: `CGPA across ${d.totalCredits} credits` }),
      el('table', { className: 'breakdown' },
        el('caption', { textContent: 'Semesters in your CGPA' }),
        el('tbody', {}, ...rows)));
  }

  function resetForm() {
    $('name').value = '';
    $('roll').value = '';
    $('semester').value = '1';
    document.querySelector('input[name="mode"][value="grades"]').checked = true;
    $('result').replaceChildren(el('p', { className: 'empty',
      textContent: 'Pick a grade for each course, then calculate. Your SGPA and CGPA appear here and are saved to your history.' }));
    loadCourses();
  }

  // ---------- history ----------
  async function loadHistory() {
    const roll = $('filter-roll').value.trim();
    const body = $('history-body');
    try {
      const rows = await api('/api/history' + (roll ? `?roll=${encodeURIComponent(roll)}` : ''));
      body.replaceChildren();
      $('history-empty').hidden = rows.length > 0;
      for (const r of rows) {
        const open = el('button', { type: 'button', className: 'link', textContent: 'View details' });
        open.addEventListener('click', () => showDetail(r.id));
        body.append(el('tr', {},
          el('td', { textContent: new Date(r.createdAt).toLocaleString() }),
          el('td', { textContent: r.name }),
          el('td', { textContent: r.roll }),
          el('td', { className: 'num', textContent: String(r.semester) }),
          el('td', { className: 'num', textContent: fmt(r.sgpa) }),
          el('td', { className: 'num', textContent: fmt(r.cgpa) }),
          el('td', {}, open)));
      }
    } catch (e) {
      body.replaceChildren(el('tr', {}, el('td', { colSpan: 7, textContent: e.message })));
    }
  }

  async function showDetail(id) {
    try {
      const d = await api(`/api/history/${id}`);
      const s = d.summary;
      const rows = d.items.map((i) => el('tr', {},
        el('td', { textContent: `${i.code} ${i.title}` }),
        el('td', { textContent: String(i.credits) }),
        el('td', { textContent: i.input }),
        el('td', { textContent: i.grade }),
        el('td', { textContent: Number(i.gradePoint).toFixed(1) })));
      $('detail-body').replaceChildren(
        el('h2', { textContent: `${s.name}, semester ${s.semester}`, style: 'font:600 1.4rem var(--serif);margin:0 0 4px' }),
        el('p', { textContent: `${s.roll}. SGPA ${fmt(s.sgpa)}, CGPA ${fmt(s.cgpa)}. Entered as ${s.mode}.` }),
        el('div', { className: 'table-wrap' }, el('table', {},
          el('thead', {}, el('tr', {},
            ...['Course', 'Credits', 'Entered', 'Grade', 'Points'].map((h) => el('th', { textContent: h })))),
          el('tbody', {}, ...rows))));
      $('detail').showModal();
    } catch (e) {
      alert(e.message);
    }
  }

  // ---------- wiring ----------
  function showView(name) {
    $('view-calc').hidden = name !== 'calc';
    $('view-history').hidden = name !== 'history';
    document.querySelectorAll('.tab').forEach((t) => {
      const on = t.dataset.view === name;
      t.classList.toggle('is-active', on);
      if (on) t.setAttribute('aria-current', 'page'); else t.removeAttribute('aria-current');
    });
    if (name === 'history') loadHistory();
  }

  async function init() {
    for (let i = 1; i <= 8; i++) $('semester').append(el('option', { value: String(i), textContent: `Semester ${i}` }));
    document.querySelectorAll('.tab').forEach((t) => t.addEventListener('click', () => showView(t.dataset.view)));
    $('semester').addEventListener('change', loadCourses);
    document.querySelectorAll('input[name="mode"]').forEach((r) => r.addEventListener('change', renderCourses));
    $('calculate').addEventListener('click', calculate);
    $('reset').addEventListener('click', resetForm);
    $('history-filter').addEventListener('submit', (e) => { e.preventDefault(); loadHistory(); });
    try {
      state.grades = await api('/api/grades');
    } catch (e) {
      setStatus(e.message);
    }
    await loadCourses();
  }
  init();
})();
