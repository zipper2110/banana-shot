/*
 * Small SVG charts for the cockpit: columns (stacked or single), lines, and sparklines.
 * Marks follow the dataviz specs: columns at most 24 px wide with a 4 px round top, 2 px lines,
 * a 2 px surface gap between stacked parts, hairline grid, and a tooltip on hover.
 * Colors come from CSS classes (f1..f5 fill, k1..k5 stroke), so that dark mode needs no code.
 */
(function () {
  'use strict';

  const SVG = 'http://www.w3.org/2000/svg';
  const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
  const PAD = { top: 10, right: 8, bottom: 24, left: 36 };

  function el(name, attrs, parent) {
    const node = document.createElementNS(SVG, name);
    for (const [key, value] of Object.entries(attrs || {})) node.setAttribute(key, value);
    if (parent) parent.appendChild(node);
    return node;
  }

  function html(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined) node.textContent = text;
    return node;
  }

  const number = new Intl.NumberFormat('en-US', { maximumFractionDigits: 1 });
  function format(value) {
    return value === null || value === undefined ? '–' : number.format(value);
  }

  function shortDate(iso) {
    const [, month, day] = iso.split('-');
    return `${MONTHS[Number(month) - 1]} ${Number(day)}`;
  }

  /** Round tick values that cover 0..max, with integer steps for counts. */
  function ticks(max, count = 4) {
    if (!(max > 0)) return [0, 1];
    const raw = max / count;
    const magnitude = Math.pow(10, Math.floor(Math.log10(raw)));
    const step = Math.max(1, [1, 2, 2.5, 5, 10].map(f => f * magnitude).find(s => s >= raw));
    const result = [];
    for (let value = 0; value < max + step; value += step) result.push(Math.round(value * 100) / 100);
    return result;
  }

  // ---------- Tooltip ----------

  const tooltip = () => document.getElementById('tooltip');

  function showTooltip(event, title, rows) {
    const tip = tooltip();
    tip.replaceChildren(html('div', 't-title', title));
    for (const row of rows) {
      const line = html('div', 't-row');
      const name = html('span');
      if (row.cls) name.appendChild(html('i', row.cls));
      name.appendChild(document.createTextNode(row.label));
      line.append(name, html('b', '', row.value));
      tip.appendChild(line);
    }
    tip.hidden = false;
    const box = tip.getBoundingClientRect();
    let x = event.clientX + 14, y = event.clientY + 14;
    if (x + box.width > window.innerWidth - 8) x = event.clientX - box.width - 14;
    if (y + box.height > window.innerHeight - 8) y = event.clientY - box.height - 14;
    tip.style.left = `${Math.max(8, x)}px`;
    tip.style.top = `${Math.max(8, y)}px`;
  }

  function hideTooltip() {
    tooltip().hidden = true;
  }

  // A scroll moves the chart away from the mouse without a mouseleave event.
  window.addEventListener('scroll', hideTooltip, { passive: true });

  // ---------- Shared frame ----------

  function legend(container, series, kind) {
    if (series.length < 2) return;
    const box = html('div', 'legend');
    for (const item of series) {
      const entry = html('span');
      entry.append(html('i', `${kind === 'line' ? 'line ' : ''}b${item.color}`), document.createTextNode(item.label));
      box.appendChild(entry);
    }
    container.appendChild(box);
  }

  function frame(container, options, max) {
    const width = Math.max(260, container.clientWidth);
    const height = options.height || 200;
    const svg = el('svg', { viewBox: `0 0 ${width} ${height}`, height, role: 'img', 'aria-label': options.label || '' });
    const plot = { x: PAD.left, y: PAD.top, w: width - PAD.left - PAD.right, h: height - PAD.top - PAD.bottom };
    const values = ticks(max);
    const top = values[values.length - 1];
    const scale = value => plot.y + plot.h - (value / top) * plot.h;
    for (const value of values) {
      const y = scale(value);
      el('line', { class: value === 0 ? 'baseline' : 'gridline', x1: plot.x, x2: plot.x + plot.w, y1: y, y2: y }, svg);
      el('text', { class: 'tick', x: plot.x - 6, y: y + 3.5, 'text-anchor': 'end' }, svg).textContent = format(value);
    }
    return { svg, plot, scale, width, height };
  }

  function xLabels(svg, plot, data, xKey, center, formatX) {
    const count = data.length;
    const every = Math.max(1, Math.ceil(count / Math.max(2, Math.floor(plot.w / 64))));
    data.forEach((item, index) => {
      if ((count - 1 - index) % every !== 0) return;
      el('text', { class: 'tick', x: center(index), y: plot.y + plot.h + 16, 'text-anchor': 'middle' }, svg)
        .textContent = (formatX || shortDate)(item[xKey]);
    });
  }

  // ---------- Columns ----------

  function columnPath(x, y, width, height, round) {
    if (height <= 0) return '';
    const r = Math.min(round ? 4 : 0, width / 2, height);
    return `M${x},${y + height}V${y + r}` + (r ? `Q${x},${y} ${x + r},${y}H${x + width - r}Q${x + width},${y} ${x + width},${y + r}` : `H${x + width}`) + `V${y + height}Z`;
  }

  function drawColumns(container, options) {
    const { data, series } = options;
    const xKey = options.x || 'date';
    const total = item => series.reduce((sum, s) => sum + (item[s.key] || 0), 0);
    const hasValue = item => series.some(s => item[s.key] !== null && item[s.key] !== undefined);
    const max = Math.max(1, ...data.map(total));
    const { svg, plot, scale } = frame(container, options, max);
    const slot = plot.w / data.length;
    const barWidth = Math.max(2, Math.min(24, slot * 0.7));
    const center = index => plot.x + slot * index + slot / 2;

    const firstValue = data.findIndex(hasValue);
    if (options.nullLabel && firstValue !== 0) {
      const end = firstValue < 0 ? data.length : firstValue;
      el('rect', { class: 'no-data', x: plot.x, y: plot.y, width: slot * end, height: plot.h, rx: 6 }, svg);
      if (slot * end > 90) {
        el('text', { class: 'no-data-label', x: plot.x + slot * end / 2, y: plot.y + plot.h / 2, 'text-anchor': 'middle' }, svg)
          .textContent = options.nullLabel;
      }
    }

    data.forEach((item, index) => {
      const band = el('rect', { class: 'hover-band', x: plot.x + slot * index, y: plot.y, width: slot, height: plot.h }, svg);
      let base = plot.y + plot.h;
      const visible = series.filter(s => (item[s.key] || 0) > 0);
      visible.forEach((s, position) => {

        const top = scale(total(item) === 0 ? 0 : visible.slice(0, position + 1).reduce((sum, v) => sum + item[v.key], 0));
        const gap = position > 0 ? 2 : 0;
        const height = base - top - gap;
        if (height > 0) {
          el('path', { class: `f${s.color}`, d: columnPath(center(index) - barWidth / 2, top, barWidth, height, position === visible.length - 1) }, svg);
        }
        base = top;
      });
      const hit = el('rect', { x: plot.x + slot * index, y: plot.y, width: slot, height: plot.h + PAD.bottom, fill: 'transparent' }, svg);
      hit.addEventListener('mousemove', event => {
        band.classList.add('on');
        const rows = series.map(s => ({ label: s.label, value: format(item[s.key]), cls: `b${s.color}` }));
        if (series.length > 1 && hasValue(item)) rows.push({ label: 'Total', value: format(total(item)) });
        const missing = index < firstValue || firstValue < 0 ? options.nullLabel : options.gapLabel || options.nullLabel;
        showTooltip(event, (options.formatTitle || options.formatX || shortDate)(item[xKey]), hasValue(item) ? rows : [{ label: missing || 'No data', value: '' }]);
      });
      hit.addEventListener('mouseleave', () => { band.classList.remove('on'); hideTooltip(); });
    });
    xLabels(svg, plot, data, xKey, center, options.formatX);
    return svg;
  }

  // ---------- Lines ----------

  function drawLines(container, options) {
    const { data, series } = options;
    const xKey = options.x || 'date';
    const max = Math.max(1, ...data.flatMap(item => series.map(s => item[s.key] || 0)));
    const { svg, plot, scale } = frame(container, options, max);
    const step = data.length > 1 ? plot.w / (data.length - 1) : 0;
    const xAt = index => plot.x + (data.length > 1 ? step * index : plot.w / 2);

    for (const s of series) {
      const points = data.map((item, index) => [xAt(index), scale(item[s.key] || 0)]);
      const d = points.map(([x, y], index) => `${index ? 'L' : 'M'}${x.toFixed(1)},${y.toFixed(1)}`).join('');
      if (options.area && series.length === 1) {
        el('path', { class: `area f${s.color}`, d: `${d}L${xAt(data.length - 1)},${plot.y + plot.h}L${xAt(0)},${plot.y + plot.h}Z` }, svg);
      }
      el('path', { class: `line k${s.color}`, d }, svg);
      const [lastX, lastY] = points[points.length - 1];
      el('circle', { class: `dot f${s.color}`, cx: lastX, cy: lastY, r: 4 }, svg);
    }

    const cross = el('line', { class: 'crosshair', y1: plot.y, y2: plot.y + plot.h, visibility: 'hidden' }, svg);
    const dots = series.map(s => el('circle', { class: `dot f${s.color}`, r: 4, visibility: 'hidden' }, svg));
    const hit = el('rect', { x: plot.x - 6, y: plot.y, width: plot.w + 12, height: plot.h, fill: 'transparent' }, svg);
    hit.addEventListener('mousemove', event => {
      const box = svg.getBoundingClientRect();
      const x = (event.clientX - box.left) * (svg.viewBox.baseVal.width / box.width);
      const index = Math.max(0, Math.min(data.length - 1, Math.round((x - plot.x) / (step || 1))));
      const item = data[index];
      cross.setAttribute('x1', xAt(index));
      cross.setAttribute('x2', xAt(index));
      cross.setAttribute('visibility', 'visible');
      series.forEach((s, position) => {
        dots[position].setAttribute('visibility', 'visible');
        dots[position].setAttribute('cx', xAt(index));
        dots[position].setAttribute('cy', scale(item[s.key] || 0));
      });
      showTooltip(event, (options.formatX || shortDate)(item[xKey]), series.map(s => ({ label: s.label, value: format(item[s.key]), cls: `b${s.color}` })));
    });
    hit.addEventListener('mouseleave', () => {
      cross.setAttribute('visibility', 'hidden');
      dots.forEach(dot => dot.setAttribute('visibility', 'hidden'));
      hideTooltip();
    });
    xLabels(svg, plot, data, xKey, xAt, options.formatX);
    return svg;
  }

  // ---------- Sparkline ----------

  function drawSpark(container, values, color) {
    const width = Math.max(80, container.clientWidth), height = 34;
    const svg = el('svg', { viewBox: `0 0 ${width} ${height}`, height, 'aria-hidden': 'true', class: 'spark' });
    const clean = values.map(value => value || 0);
    const max = Math.max(1, ...clean);
    const step = clean.length > 1 ? (width - 6) / (clean.length - 1) : 0;
    const points = clean.map((value, index) => [3 + step * index, height - 4 - (value / max) * (height - 8)]);
    const d = points.map(([x, y], index) => `${index ? 'L' : 'M'}${x.toFixed(1)},${y.toFixed(1)}`).join('');
    el('path', { class: `area f${color}`, d: `${d}L${points[points.length - 1][0]},${height}L3,${height}Z` }, svg);
    el('path', { class: `line k${color}`, d }, svg);
    const [x, y] = points[points.length - 1];
    el('circle', { class: `dot f${color}`, cx: x, cy: y, r: 3 }, svg);
    return svg;
  }

  // ---------- Mounting and resize ----------

  const mounted = new Map();
  const observer = new ResizeObserver(entries => {
    for (const entry of entries) {
      const render = mounted.get(entry.target);
      if (render && Math.abs(entry.contentRect.width - (entry.target._lastWidth || 0)) > 4) render();
    }
  });

  function mount(container, draw) {
    const render = () => {
      container._lastWidth = container.clientWidth;
      container.replaceChildren();
      draw();
    };
    if (!mounted.has(container)) observer.observe(container);
    mounted.set(container, render);
    render();
  }

  window.Charts = {
    format,
    shortDate,
    hideTooltip,
    showTooltip,
    columns(container, options) {
      mount(container, () => {
        legend(container, options.series, 'column');
        container.appendChild(drawColumns(container, options));
      });
    },
    lines(container, options) {
      mount(container, () => {
        legend(container, options.series, 'line');
        container.appendChild(drawLines(container, options));
      });
    },
    spark(container, values, color) {
      mount(container, () => container.appendChild(drawSpark(container, values, color)));
    },
  };
})();
