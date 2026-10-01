/* =====================================================================
   AdOptimize Pro — lightweight canvas charts (no external libraries)
   Charts.line(canvas, labels, datasets, options)
   Charts.bar(canvas, labels, datasets, options)
   Charts.doughnut(canvas, labels, values, options)
   ===================================================================== */
'use strict';

const Charts = (() => {
  const PALETTE = ['#6c63ff', '#00d4ff', '#22d3a5', '#f59e0b', '#f43f5e', '#a78bfa', '#38bdf8'];
  const GRID = 'rgba(255,255,255,0.06)';
  const AXIS_TEXT = '#6d7596';
  const FONT = '11px Inter, system-ui, sans-serif';

  /** Sizes the canvas for the device pixel ratio and returns a context using CSS pixels. */
  function prepare(canvas) {
    const container = canvas.parentElement;
    const width = container.clientWidth || canvas.clientWidth || 300;
    const height = container.clientHeight || Number(canvas.getAttribute('height')) || 220;
    const ratio = window.devicePixelRatio || 1;
    canvas.style.width = width + 'px';
    canvas.style.height = height + 'px';
    canvas.width = Math.round(width * ratio);
    canvas.height = Math.round(height * ratio);
    const ctx = canvas.getContext('2d');
    ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
    ctx.clearRect(0, 0, width, height);
    return { ctx, width, height };
  }

  function niceMax(value) {
    if (!isFinite(value) || value <= 0) return 1;
    const exponent = Math.pow(10, Math.floor(Math.log10(value)));
    const fraction = value / exponent;
    // Every candidate divides evenly into the 4 grid steps.
    const nice = fraction <= 1 ? 1 : fraction <= 2 ? 2 : fraction <= 4 ? 4 : fraction <= 6 ? 6 : fraction <= 8 ? 8 : 10;
    return nice * exponent;
  }

  function shortNumber(value) {
    const n = Math.abs(value);
    if (n >= 1e6) return (value / 1e6).toFixed(1).replace(/\.0$/, '') + 'M';
    if (n >= 1e3) return (value / 1e3).toFixed(1).replace(/\.0$/, '') + 'K';
    return String(Math.round(value * 100) / 100);
  }

  function setEmpty(canvas, isEmpty, message) {
    const container = canvas.parentElement;
    let overlay = container.querySelector('.chart-empty');
    if (isEmpty) {
      if (!overlay) {
        overlay = document.createElement('div');
        overlay.className = 'chart-empty';
        container.appendChild(overlay);
      }
      overlay.textContent = message || 'No data for this period yet';
    } else if (overlay) {
      overlay.remove();
    }
  }

  function tooltip(canvas) {
    const container = canvas.parentElement;
    let tip = container.querySelector('.chart-tooltip');
    if (!tip) {
      tip = document.createElement('div');
      tip.className = 'chart-tooltip hidden';
      container.appendChild(tip);
    }
    return tip;
  }

  function drawLegend(ctx, datasets, width) {
    ctx.font = FONT;
    let x = width;
    for (let i = datasets.length - 1; i >= 0; i--) {
      const label = datasets[i].label || '';
      const textWidth = ctx.measureText(label).width;
      x -= textWidth + 26;
      ctx.fillStyle = datasets[i].color || PALETTE[i % PALETTE.length];
      ctx.beginPath();
      ctx.arc(x + 5, 9, 4, 0, Math.PI * 2);
      ctx.fill();
      ctx.fillStyle = '#a4acc9';
      ctx.textAlign = 'left';
      ctx.textBaseline = 'middle';
      ctx.fillText(label, x + 14, 9);
    }
  }

  /** Shared axes/grid for line and bar charts; returns the plot geometry. */
  function drawAxes(ctx, width, height, labels, max, formatY, rightMax, formatRight) {
    const pad = { top: 28, right: rightMax ? 44 : 12, bottom: 28, left: 48 };
    const plotW = Math.max(10, width - pad.left - pad.right);
    const plotH = Math.max(10, height - pad.top - pad.bottom);
    ctx.font = FONT;
    ctx.textBaseline = 'middle';
    const steps = 4;
    for (let i = 0; i <= steps; i++) {
      const y = pad.top + plotH - (plotH * i) / steps;
      ctx.strokeStyle = GRID;
      ctx.lineWidth = 1;
      ctx.beginPath();
      ctx.moveTo(pad.left, Math.round(y) + 0.5);
      ctx.lineTo(pad.left + plotW, Math.round(y) + 0.5);
      ctx.stroke();
      ctx.fillStyle = AXIS_TEXT;
      ctx.textAlign = 'right';
      ctx.fillText(formatY((max * i) / steps), pad.left - 8, y);
      if (rightMax) {
        ctx.textAlign = 'left';
        ctx.fillText((formatRight || shortNumber)((rightMax * i) / steps), pad.left + plotW + 8, y);
      }
    }
    // X labels: skip some when crowded
    ctx.textAlign = 'center';
    ctx.textBaseline = 'top';
    const slot = plotW / Math.max(labels.length, 1);
    const every = Math.max(1, Math.ceil(56 / slot));
    const shown = [];
    for (let i = 0; i < labels.length; i += every) shown.push(i);
    const last = labels.length - 1;
    if (last > 0 && shown[shown.length - 1] !== last) {
      // Always label the newest point; drop the previous label if the two would collide.
      if (last - shown[shown.length - 1] < every) shown.pop();
      shown.push(last);
    }
    shown.forEach((i) => {
      ctx.fillStyle = AXIS_TEXT;
      ctx.fillText(labels[i], pad.left + slot * i + slot / 2, pad.top + plotH + 9);
    });
    return { pad, plotW, plotH, slot };
  }

  function registerRedraw(canvas, fn) {
    canvas._redraw = fn;
    canvas.classList.add('chart-canvas');
    fn();
  }

  function line(canvas, labels, datasets, options = {}) {
    if (!canvas) return;
    const formatY = options.formatY || shortNumber;
    const formatValue = options.formatValue || ((v) => Number(v).toLocaleString('en-US'));
    registerRedraw(canvas, () => {
      const { ctx, width, height } = prepare(canvas);
      const allValues = datasets.flatMap((d) => d.data.map(Number));
      const isEmpty = !labels.length || allValues.every((v) => !v);
      setEmpty(canvas, isEmpty, options.emptyMessage);
      // Series marked axis:'right' get their own scale (e.g. clicks next to impressions).
      const leftValues = datasets.filter((d) => d.axis !== 'right').flatMap((d) => d.data.map(Number));
      const rightSets = datasets.filter((d) => d.axis === 'right');
      const rightValues = rightSets.flatMap((d) => d.data.map(Number));
      const max = niceMax(Math.max(...leftValues, 0) * 1.1);
      const rightMax = rightSets.length ? niceMax(Math.max(...rightValues, 0) * 1.1) : 0;
      const { pad, plotH, slot } = drawAxes(ctx, width, height, labels, max, formatY, rightMax, options.formatRight);
      if (datasets.length > 1 || options.legend) drawLegend(ctx, datasets, width);

      const pointX = (i) => pad.left + slot * i + slot / 2;
      const pointY = (v, scaleMax) => pad.top + plotH - (plotH * Number(v)) / scaleMax;

      datasets.forEach((dataset, index) => {
        const color = dataset.color || PALETTE[index % PALETTE.length];
        const scaleMax = dataset.axis === 'right' ? rightMax : max;
        const points = dataset.data.map((v, i) => [pointX(i), pointY(v, scaleMax)]);
        if (!points.length) return;

        const path = new Path2D();
        points.forEach(([x, y], i) => {
          if (i === 0) { path.moveTo(x, y); return; }
          const [px, py] = points[i - 1];
          const cx = (px + x) / 2;
          path.bezierCurveTo(cx, py, cx, y, x, y);
        });

        if (dataset.fill !== false) {
          const area = new Path2D(path);
          area.lineTo(points[points.length - 1][0], pad.top + plotH);
          area.lineTo(points[0][0], pad.top + plotH);
          area.closePath();
          const gradient = ctx.createLinearGradient(0, pad.top, 0, pad.top + plotH);
          gradient.addColorStop(0, color + '40');
          gradient.addColorStop(1, color + '00');
          ctx.fillStyle = gradient;
          ctx.fill(area);
        }
        ctx.strokeStyle = color;
        ctx.lineWidth = 2.2;
        ctx.lineJoin = 'round';
        ctx.stroke(path);
        if (points.length <= 31) {
          points.forEach(([x, y]) => {
            ctx.fillStyle = '#0a0e1c';
            ctx.beginPath();
            ctx.arc(x, y, 3, 0, Math.PI * 2);
            ctx.fill();
            ctx.strokeStyle = color;
            ctx.lineWidth = 1.8;
            ctx.stroke();
          });
        }
      });

      canvas._hover = (offsetX) => {
        const i = Math.floor((offsetX - pad.left) / slot);
        if (i < 0 || i >= labels.length) return null;
        return {
          x: pointX(i),
          html: `<strong>${esc(labels[i])}</strong>` + datasets.map((d, di) =>
            `<div><span style="color:${d.color || PALETTE[di % PALETTE.length]}">●</span> ${esc(d.label || '')}: ${esc(formatValue(d.data[i]))}</div>`).join(''),
        };
      };
    });
    attachHover(canvas);
  }

  function bar(canvas, labels, datasets, options = {}) {
    if (!canvas) return;
    const formatY = options.formatY || shortNumber;
    const formatValue = options.formatValue || ((v) => Number(v).toLocaleString('en-US'));
    registerRedraw(canvas, () => {
      const { ctx, width, height } = prepare(canvas);
      const allValues = datasets.flatMap((d) => d.data.map(Number));
      setEmpty(canvas, !labels.length || allValues.every((v) => !v), options.emptyMessage);
      const max = niceMax(Math.max(...allValues, 0) * 1.1);
      const { pad, plotH, slot } = drawAxes(ctx, width, height, labels, max, formatY);
      if (datasets.length > 1 || options.legend) drawLegend(ctx, datasets, width);

      const groupWidth = slot * 0.64;
      const barWidth = groupWidth / datasets.length;
      datasets.forEach((dataset, di) => {
        const color = dataset.color || PALETTE[di % PALETTE.length];
        dataset.data.forEach((value, i) => {
          const h = (plotH * Number(value)) / max;
          const x = pad.left + slot * i + (slot - groupWidth) / 2 + barWidth * di + 1;
          const y = pad.top + plotH - h;
          const w = Math.max(2, barWidth - 2);
          const radius = Math.min(5, w / 2, h);
          const gradient = ctx.createLinearGradient(0, y, 0, pad.top + plotH);
          gradient.addColorStop(0, color);
          gradient.addColorStop(1, color + '66');
          ctx.fillStyle = gradient;
          ctx.beginPath();
          if (ctx.roundRect) ctx.roundRect(x, y, w, h, [radius, radius, 0, 0]);
          else ctx.rect(x, y, w, h);
          ctx.fill();
        });
      });

      canvas._hover = (offsetX) => {
        const i = Math.floor((offsetX - pad.left) / slot);
        if (i < 0 || i >= labels.length) return null;
        return {
          x: pad.left + slot * i + slot / 2,
          html: `<strong>${esc(labels[i])}</strong>` + datasets.map((d, di) =>
            `<div><span style="color:${d.color || PALETTE[di % PALETTE.length]}">●</span> ${esc(d.label || '')}: ${esc(formatValue(d.data[i]))}</div>`).join(''),
        };
      };
    });
    attachHover(canvas);
  }

  function doughnut(canvas, labels, values, options = {}) {
    if (!canvas) return;
    const colors = options.colors || PALETTE;
    registerRedraw(canvas, () => {
      const { ctx, width, height } = prepare(canvas);
      const total = values.reduce((sum, v) => sum + Number(v), 0);
      setEmpty(canvas, false);
      const legendWidth = width > 360 ? Math.min(180, width * 0.45) : 0;
      const size = Math.min(width - legendWidth, height) - 10;
      const radius = Math.max(20, size / 2);
      const cx = legendWidth ? (width - legendWidth) / 2 : width / 2;
      const cy = height / 2;
      const thickness = radius * 0.32;

      if (total <= 0) {
        ctx.strokeStyle = 'rgba(255,255,255,0.08)';
        ctx.lineWidth = thickness;
        ctx.beginPath();
        ctx.arc(cx, cy, radius - thickness / 2, 0, Math.PI * 2);
        ctx.stroke();
      } else {
        let angle = -Math.PI / 2;
        values.forEach((value, i) => {
          const sweep = (Number(value) / total) * Math.PI * 2;
          if (sweep <= 0) return;
          ctx.strokeStyle = colors[i % colors.length];
          ctx.lineWidth = thickness;
          ctx.beginPath();
          ctx.arc(cx, cy, radius - thickness / 2, angle, angle + Math.max(sweep - 0.02, 0.001));
          ctx.stroke();
          angle += sweep;
        });
      }

      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.fillStyle = '#e9ecf8';
      ctx.font = `700 ${Math.max(14, radius * 0.3)}px Outfit, Inter, sans-serif`;
      ctx.fillText(options.centerText !== undefined ? options.centerText : String(total), cx, cy - (options.centerLabel ? 8 : 0));
      if (options.centerLabel) {
        ctx.fillStyle = AXIS_TEXT;
        ctx.font = FONT;
        ctx.fillText(options.centerLabel, cx, cy + radius * 0.2);
      }

      if (legendWidth) {
        ctx.textAlign = 'left';
        ctx.font = FONT;
        const rowHeight = 20;
        const startY = cy - (labels.length * rowHeight) / 2 + rowHeight / 2;
        labels.forEach((label, i) => {
          const y = startY + i * rowHeight;
          const x = width - legendWidth + 8;
          ctx.fillStyle = colors[i % colors.length];
          ctx.beginPath();
          ctx.arc(x + 5, y, 5, 0, Math.PI * 2);
          ctx.fill();
          ctx.fillStyle = '#a4acc9';
          const pct = total > 0 ? Math.round((Number(values[i]) / total) * 100) : 0;
          let text = `${label} · ${options.valueLabels ? options.valueLabels[i] : values[i]}`;
          if (options.showPercent) text = `${label} · ${pct}%`;
          while (ctx.measureText(text).width > legendWidth - 24 && text.length > 4) text = text.slice(0, -2) + '…';
          ctx.fillText(text, x + 16, y);
        });
      }
      canvas._hover = null;
    });
  }

  function attachHover(canvas) {
    if (canvas._hoverAttached) return;
    canvas._hoverAttached = true;
    canvas.addEventListener('mousemove', (event) => {
      const tip = tooltip(canvas);
      const info = canvas._hover ? canvas._hover(event.offsetX) : null;
      if (!info) { tip.classList.add('hidden'); return; }
      tip.innerHTML = info.html;
      tip.classList.remove('hidden');
      const containerWidth = canvas.parentElement.clientWidth;
      const left = Math.min(Math.max(0, info.x + 12), containerWidth - tip.offsetWidth - 4);
      tip.style.left = left + 'px';
      tip.style.top = Math.max(0, event.offsetY - tip.offsetHeight - 10) + 'px';
    });
    canvas.addEventListener('mouseleave', () => tooltip(canvas).classList.add('hidden'));
  }

  /** Redraws every visible chart (after resizes or when a hidden section becomes visible). */
  function redrawAll() {
    document.querySelectorAll('canvas.chart-canvas').forEach((canvas) => {
      if (canvas._redraw && canvas.offsetParent !== null) canvas._redraw();
    });
  }

  window.addEventListener('resize', debounce(redrawAll, 150));

  return { line, bar, doughnut, redrawAll, PALETTE };
})();
