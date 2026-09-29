/*
 * Accent color test for the Colors tab mockup (accent.html).
 * It computes all accent shades in shared.css from one color and sets them on :root.
 * The hash keeps the color (#accent=a3cf59), so a reload shows the same color.
 */
(function () {
  const BG = '#0e0e0e'; // DARK_BG

  const ACCENTS = [
    {
      id: 'lime', name: 'Lime', hex: '#a1fe00', ref: true,
      note: 'The current accent (UiStyles.LIME). It has 100% saturation, so it looks acidic next to the gray UI.',
    },
    {
      id: 'moss', name: 'Moss', hex: '#a3cf59',
      note: 'The same hue as the current lime, with about half the saturation. This is the smallest change: the app keeps its look.',
    },
    {
      id: 'straw', name: 'Straw', hex: '#d1c575',
      note: 'Nearer to yellow, with low saturation, like a used tennis ball. ' +
        'Risk: it is near the warning yellow (#f2d64b). Set "Live preview" to "Not supported" to compare the two.',
    },
    {
      id: 'mint', name: 'Mint', hex: '#66cc96',
      note: 'A cool green. It is calm on the dark UI. Risk: many courts have green surrounds, as in this frame.',
    },
    {
      id: 'blue', name: 'Court blue', hex: '#7babf4',
      note: 'Hard-court blue. It has no conflict with the warning yellow or the error red. Risk: it is near the court color in this frame.',
    },
    {
      id: 'lavender', name: 'Lavender', hex: '#ac9fef',
      note: 'Far from all court colors and all status colors, so the controls never blend with the video. It has less of a tennis look.',
    },
  ];

  // ---------- Color math (sRGB) ----------
  const clamp = (v, lo, hi) => Math.max(lo, Math.min(hi, v));
  function toRgb(hex) {
    const n = parseInt(hex.slice(1), 16);
    return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
  }
  const toHex = (rgb) => '#' + rgb.map((v) => Math.round(clamp(v, 0, 255)).toString(16).padStart(2, '0')).join('');
  // t = the part of color b in the result.
  const mix = (a, b, t) => toHex(toRgb(a).map((v, i) => v + (toRgb(b)[i] - v) * t));
  const rgba = (hex, a) => `rgba(${toRgb(hex).join(', ')}, ${a})`;

  function toHsl(hex) {
    const [r, g, b] = toRgb(hex).map((v) => v / 255);
    const max = Math.max(r, g, b), min = Math.min(r, g, b), l = (max + min) / 2, d = max - min;
    if (d === 0) return [0, 0, Math.round(l * 100)];
    const s = d / (1 - Math.abs(2 * l - 1));
    let h = max === r ? ((g - b) / d) % 6 : max === g ? (b - r) / d + 2 : (r - g) / d + 4;
    h = (h * 60 + 360) % 360;
    return [Math.round(h), Math.round(s * 100), Math.round(l * 100)];
  }
  function fromHsl(h, s, l) {
    s /= 100; l /= 100;
    const c = (1 - Math.abs(2 * l - 1)) * s, x = c * (1 - Math.abs(((h / 60) % 2) - 1)), m = l - c / 2;
    const [r, g, b] = h < 60 ? [c, x, 0] : h < 120 ? [x, c, 0] : h < 180 ? [0, c, x] : h < 240 ? [0, x, c] : h < 300 ? [x, 0, c] : [c, 0, x];
    return toHex([r, g, b].map((v) => (v + m) * 255));
  }

  function luminance(hex) {
    const [r, g, b] = toRgb(hex).map((v) => {
      v /= 255;
      return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    });
    return 0.2126 * r + 0.7152 * g + 0.0722 * b;
  }
  function contrast(a, b) {
    const [x, y] = [luminance(a), luminance(b)].sort((p, q) => q - p);
    return (x + 0.05) / (y + 0.05);
  }

  // The mix ratios give the current shades in shared.css when the accent is #a1fe00.
  function shades(hex) {
    return {
      '--lime': hex,
      '--lime-hover': mix(hex, '#ffffff', 0.2),
      '--lime-tint': rgba(hex, 0.07),
      '--lime-line': rgba(hex, 0.6),
      '--lime-glow': rgba(hex, 0.25),
      '--lime-shadow': rgba(hex, 0.14),
      '--on-lime': mix(hex, '#000000', 0.88),
      '--on-lime-2': mix(hex, '#000000', 0.58),
      '--green': mix(hex, '#ffffff', 0.1),
      '--sage': mix(hex, '#aaaaaa', 0.65),
    };
  }

  // ---------- UI ----------
  let current = ACCENTS[1].hex;
  const $ = (sel) => document.querySelector(sel);

  function renderBar() {
    const bar = $('#accent-bar');
    const hslRow = (key, label, max) =>
      `<span>${label}</span><input type="range" min="0" max="${max}" data-hsl="${key}" aria-label="${label}"><output data-hsl-out="${key}"></output>`;
    bar.innerHTML =
      '<span class="mock-label">Accent</span>' +
      '<div class="swatches">' + ACCENTS.map((a) =>
        `<button class="swatch ${a.ref ? 'is-ref' : ''}" data-accent="${a.hex}" style="--c:${a.hex}" title="${a.note}">` +
        `<i></i><b>${a.name}${a.ref ? ' (current)' : ''}</b><small>${a.hex}</small></button>`).join('') +
      '</div><span class="sep"></span>' +
      '<div class="custom">' +
      '<input type="color" data-picker aria-label="Pick any color" title="Pick any color">' +
      '<input class="hex" data-hex spellcheck="false" maxlength="7" aria-label="Hex color">' +
      '<div class="hsl">' + hslRow('h', 'H', 359) + hslRow('s', 'S', 100) + hslRow('l', 'L', 100) + '</div>' +
      '</div>' +
      '<div class="readout">' +
      '<span data-c-bg title="Accent on the app background. 3:1 is the minimum for controls and large text.">On background <b></b></span>' +
      '<span data-c-on title="Icon and text on the accent, for example the play button. 4.5:1 is the minimum for text.">Text on accent <b></b></span>' +
      '</div>';

    bar.addEventListener('click', (ev) => {
      const b = ev.target.closest('[data-accent]');
      if (b) { apply(b.dataset.accent); b.blur(); }
    });
    bar.querySelector('[data-picker]').addEventListener('input', (ev) => apply(ev.target.value, 'picker'));
    bar.querySelector('[data-hex]').addEventListener('input', (ev) => {
      let v = ev.target.value.trim().toLowerCase();
      if (!v.startsWith('#')) v = '#' + v;
      if (/^#[0-9a-f]{3}$/.test(v)) v = '#' + [...v.slice(1)].map((c) => c + c).join('');
      const ok = /^#[0-9a-f]{6}$/.test(v);
      ev.target.classList.toggle('is-bad', !ok);
      if (ok) apply(v, 'hex');
    });
    bar.querySelectorAll('[data-hsl]').forEach((el) => el.addEventListener('input', () => {
      const [h, s, l] = ['h', 's', 'l'].map((k) => Number(bar.querySelector(`[data-hsl="${k}"]`).value));
      apply(fromHsl(h, s, l), 'hsl');
    }));
  }

  function apply(hex, source) {
    current = hex.toLowerCase();
    const root = document.documentElement.style;
    Object.entries(shades(current)).forEach(([k, v]) => root.setProperty(k, v));

    const bar = $('#accent-bar');
    if (source !== 'picker') bar.querySelector('[data-picker]').value = current;
    if (source !== 'hex') {
      const hexEl = bar.querySelector('[data-hex]');
      hexEl.value = current;
      hexEl.classList.remove('is-bad');
    }
    const hsl = toHsl(current);
    ['h', 's', 'l'].forEach((k, i) => {
      // Keep the slider positions while the user drags them, because the hex rounding moves the values a little.
      if (source !== 'hsl') bar.querySelector(`[data-hsl="${k}"]`).value = hsl[i];
      bar.querySelector(`[data-hsl-out="${k}"]`).textContent = hsl[i] + (k === 'h' ? '°' : '%');
    });
    const [h, s, l] = hsl;
    bar.querySelector('[data-hsl="h"]').style.setProperty('--g',
      `linear-gradient(90deg, ${[0, 60, 120, 180, 240, 300, 360].map((x) => fromHsl(x % 360, s, l)).join(', ')})`);
    bar.querySelector('[data-hsl="s"]').style.setProperty('--g', `linear-gradient(90deg, ${fromHsl(h, 0, l)}, ${fromHsl(h, 100, l)})`);
    bar.querySelector('[data-hsl="l"]').style.setProperty('--g', `linear-gradient(90deg, #000, ${fromHsl(h, s, 50)}, #fff)`);

    const cBg = contrast(current, BG), cOn = contrast(current, shades(current)['--on-lime']);
    const show = (sel, v, min) => {
      const el = bar.querySelector(sel);
      el.querySelector('b').textContent = v.toFixed(1) + ':1';
      el.classList.toggle('low', v < min);
    };
    show('[data-c-bg]', cBg, 3);
    show('[data-c-on]', cOn, 4.5);

    const preset = ACCENTS.find((a) => a.hex === current);
    bar.querySelectorAll('[data-accent]').forEach((b) => b.classList.toggle('is-selected', b.dataset.accent === current));
    $('#accent-note').innerHTML = preset
      ? `<b>${preset.name} ${preset.hex}.</b> ${preset.note}`
      : `<b>Custom ${current}.</b> H ${h}°, S ${s}%, L ${l}%. Use the swatches to compare it with the proposals.`;

    const params = new URLSearchParams(location.hash.slice(1));
    params.set('accent', current.slice(1));
    history.replaceState(null, '', '#' + params.toString());
  }

  function init() {
    renderBar();
    const fromHash = new URLSearchParams(location.hash.slice(1)).get('accent');
    apply(/^[0-9a-fA-F]{6}$/.test(fromHash || '') ? '#' + fromHash : current);
  }

  window.Accent = { ACCENTS, init, apply, shades };
})();
