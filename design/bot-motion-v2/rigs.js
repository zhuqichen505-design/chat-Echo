/* Original Echo character rigs. 100 × 100 artboard; no assets or dependencies. */
(() => {
'use strict';

const BOT_CATALOG = [
  {
    id: 'tuantuan', name: '团团', subtitle: '慢半拍的小软团', accent: '#E58C73',
    description: '圆鼓鼓的身体、小鳍一样的手和藏不住的小脚。先好奇地凑近，再认真听你说。',
    traits: ['软团变形', '小鳍跟随', '探头与侧耳'],
    motions: { connecting: '踮脚探头，左右侧耳寻找信号', thinking: '眼神游走，小鳍托腮，间或轻轻点头', replying: '随说话节奏捧出小光点，身体有轻重不同的起伏', completed: '蹲一下，开心地蹦起挥手，落地回弹后眯眼微笑' },
  },
  {
    id: 'fangbo', name: '方啵', subtitle: '动作认真的小方盒', accent: '#62A99A',
    description: '像口袋玩具的小方盒，有会伸缩的短手和总慢一点才停下的弹簧触角。',
    traits: ['伸缩短臂', '弹簧触角', '预备与回弹'],
    motions: { connecting: '触角弹开，屏幕目光来回扫描，短臂试着接住信号', thinking: '侧头托下巴，另一只手轻点，触角慢慢垂下来', replying: '两只手错拍传递消息，眼神跟随，屏幕口型随音节变化', completed: '压低蓄力，举手小跳，触角滞后晃两下再收好' },
  },
  {
    id: 'jiujiu', name: '啾啾', subtitle: '浮在空中的传信胶囊', accent: '#D2A448',
    description: '两片小翼托着横向胶囊，尾翼像一枚书签。它会绕着念头飞，也会把话递到你面前。',
    traits: ['分段翼拍', '悬停与停顿', '尾翼滞后'],
    motions: { connecting: '小翼分段扑动，探身后退，目光寻找连接点', thinking: '沿小小的八字悬停，目光先走，尾翼稍后跟上', replying: '小翼交替递话，随重音前倾，光点向前跳出', completed: '收翼蓄力，展开小翼画出短弧，轻轻停在原位' },
  },
  {
    id: 'zhedou', name: '折豆', subtitle: '一板一眼也会雀跃', accent: '#91A36A',
    description: '带缺角的小积木，细折臂、方脚掌、两只高低不同的眼睛。每次迈步都很认真。',
    traits: ['机械步态', '折线手臂', '两拍小庆祝'],
    motions: { connecting: '左右两步原地巡逻，停下抬手打招呼，再继续扫描', thinking: '踱两小步，扶住侧额，方眼一格一格地移过去', replying: '双手像敲小键盘般错拍工作，方嘴和小脚回应重音', completed: '先缩成小块，再左右举手踏两步，合脚站好' },
  },
];

const TAU = Math.PI * 2;
const STROKE_SCALE = Symbol('echoBotStrokeScale');
const clamp = (v, a = 0, b = 1) => Math.max(a, Math.min(b, v));
const mix = (a, b, t) => a + (b - a) * t;
const smooth = (a, b, x) => { const t = clamp((x - a) / (b - a)); return t * t * (3 - 2 * t); };
const pulse = (x, start, peak, end) => smooth(start, peak, x) * (1 - smooth(peak, end, x));
const mod = (v, n) => ((v % n) + n) % n;
const sin = (t, period, phase = 0) => Math.sin(t * TAU / period + phase);
const stateName = value => String(value || 'idle').toLowerCase().replace('streaming', 'replying').replace('neutral', 'idle');

function blinkAt(t, shift) {
  const a = mod(t + shift, 5.9);
  const b = mod(t + shift * 0.71, 13.7);
  return Math.max(pulse(a, 4.94, 5.025, 5.145), pulse(b, 9.98, 10.06, 10.18) * 0.92);
}

function thinkingEyes(t, shift) {
  // Eyes arrive first and hold; the body follows later instead of oscillating in sync.
  const p = mod(t + shift, 5.6);
  const x = p < 1.45 ? mix(-0.2, 1, smooth(0.12, 0.37, p))
    : p < 3.3 ? mix(1, -0.85, smooth(1.8, 2.05, p))
      : mix(-0.85, -0.2, smooth(4.6, 4.92, p));
  return { x, y: -0.8 + 0.15 * sin(t, 3.1, shift), p };
}

function replyBeat(t, shift) {
  const p = mod(t + shift, 2.73);
  // A phrase has short syllables, a stressed syllable and a deliberate rest.
  const a = pulse(p, 0.04, 0.12, 0.25);
  const b = pulse(p, 0.34, 0.44, 0.63) * 0.62;
  const c = pulse(p, 0.79, 0.95, 1.23);
  const d = pulse(p, 1.38, 1.46, 1.63) * 0.45;
  const e = pulse(p, 1.76, 1.96, 2.28) * 0.8;
  return { mouth: Math.max(a, b, c, d, e), stress: c, gesture: pulse(p, 0.62, 1.03, 1.65), second: pulse(p, 1.64, 2.04, 2.58), p };
}

function completion(age) {
  return {
    prepare: pulse(age, 0, 0.16, 0.32),
    jump: pulse(age, 0.17, 0.48, 0.91),
    land: pulse(age, 0.75, 0.96, 1.14),
    wave: pulse(age, 0.24, 0.56, 1.2),
    settle: age > 0.84 && age < 1.64 ? Math.sin((age - 0.84) * 21) * Math.exp(-(age - 0.84) * 5.8) : 0,
    smile: smooth(0.24, 0.66, age),
    sparkle: pulse(age, 0.3, 0.67, 1.35),
  };
}

/** Deterministic rig channels. Useful for checking real limb / gaze / body motion. */
function samplePose(id, options = {}) {
  const state = stateName(options.state);
  const reduced = !!options.reducedMotion;
  const t = reduced ? 0 : Number(options.time || 0);
  const age = reduced ? 2 : Math.max(0, Number(options.stateAge || 0));
  const weights = options.weights || { [state]: 1 };
  const w = key => clamp(Number(weights[key] ?? (state === key && !options.weights ? 1 : 0)) || 0);
  const c = w('connecting'); const h = w('thinking'); const r = w('replying'); const f = w('completed');
  const shift = ['tuantuan', 'fangbo', 'jiujiu', 'zhedou'].indexOf(id) * 0.79;
  const eye = thinkingEyes(t, shift); const beat = replyBeat(t, shift);
  const doneAge = reduced ? 2 : Number(options.completionAge ?? (state === 'completed' ? age : 2));
  const done = completion(doneAge);
  const pointer = reduced ? { x: 0, y: 0 } : options.pointer || { x: 0, y: 0 };
  const touch = reduced ? 0 : clamp(Number(options.interaction || 0));
  const p = {
    x: 0, y: reduced ? 0 : sin(t, 4.1, shift) * 0.48,
    rotation: 0, scaleX: 1, scaleY: 1,
    gazeX: clamp(Number(pointer.x || 0), -1, 1) * 1.5,
    gazeY: clamp(Number(pointer.y || 0), -1, 1) * 1,
    blink: reduced ? 0 : blinkAt(t, shift),
    eyeSmile: f * done.smile + touch * 0.7,
    mouth: r * beat.mouth, smile: f * done.smile,
    armL: 0, armR: 0, extendL: 0, extendR: 0,
    footL: 0, footR: 0, antenna: 0, wingL: 0, wingR: 0, tail: 0,
    glow: 0, sparkle: f * done.sparkle, thinking: h, connecting: c, replying: r, completed: f,
  };
  if (reduced) {
    p.gazeX = h * 1.4; p.gazeY = -h * 1.6;
    p.mouth = r * 0.28; p.armL = -r * 0.12; p.armR = -h * 0.8 - c * 0.3 - f * 0.55;
    p.antenna = c * 0.2; p.wingL = -r * 0.12; p.wingR = r * 0.12;
    return p;
  }

  if (id === 'tuantuan') {
    const seek = sin(t, 3.55); const peep = pulse(mod(t, 3.55), 0.4, 0.85, 1.75);
    p.x += c * seek * 2 + h * eye.x * 0.6;
    p.y += c * (-1.7 * peep) + h * sin(t, 4.7, 0.8) * 0.6 - r * beat.stress * 2.2;
    p.rotation += c * seek * 0.115 + h * eye.x * 0.065 + r * (beat.gesture - beat.second) * 0.05;
    p.scaleX += r * beat.stress * 0.018 + f * (done.prepare * 0.075 - done.jump * 0.04 + done.land * 0.05);
    p.scaleY += r * beat.stress * -0.025 + f * (-done.prepare * 0.11 + done.jump * 0.07 - done.land * 0.065);
    p.gazeX += c * seek * 2 + h * eye.x * 2.2 + r * sin(t, 3.7) * 0.6;
    p.gazeY += -c * peep * 1.2 + h * eye.y * 2.1;
    p.armL = c * (-0.35 - peep * 0.35) + h * (0.15 + sin(t, 4.9) * 0.08) - r * beat.second * 0.68;
    p.armR = c * (0.2 + seek * 0.22) - h * (0.78 + sin(t, 4.1) * 0.07) + r * (0.15 - beat.gesture * 0.95);
    p.footL = c * peep * 0.55 + r * beat.second * 0.2;
    p.footR = c * (1 - peep) * 0.3 + r * beat.gesture * 0.2;
    p.glow = r * beat.gesture;
    p.y += f * (done.prepare * 2 - done.jump * 9 + done.land * 2.2 + done.settle * 0.45);
    p.rotation += f * (-done.jump * 0.08 + done.settle * 0.06);
    p.armL += f * (-done.wave * 0.92);
    p.armR += f * (-done.wave * (1.05 + Math.sin(doneAge * 24) * 0.22));
    p.footL += f * done.jump * 0.55; p.footR -= f * done.jump * 0.35;
  } else if (id === 'fangbo') {
    const search = sin(t, 3.2); const tap = pulse(mod(t, 2.9), 1.66, 1.81, 2.02);
    p.x += c * search * 0.75;
    p.y += c * sin(t, 1.6) * 0.55 + h * tap * 0.6 - r * beat.stress * 1;
    p.rotation += c * search * 0.045 + h * (0.09 + eye.x * 0.035) + r * (beat.gesture - beat.second) * 0.033;
    p.gazeX += c * search * 3.1 + h * eye.x * 2 + r * (beat.second - beat.gesture) * 1.2;
    p.gazeY += -h * 1.3 - c * 0.45;
    p.antenna = c * (0.25 + sin(t, 0.82) * 0.2) - h * (0.15 + tap * 0.25) + r * sin(t, 1.31) * 0.13;
    p.armL = -c * (0.2 + 0.35 * (search * 0.5 + 0.5)) - h * (0.02 + tap * 0.2) - r * beat.second * 0.8;
    p.armR = -c * (0.2 + 0.35 * (-search * 0.5 + 0.5)) + h * (1.1 + sin(t, 4.2) * 0.06) - r * beat.gesture * 0.82;
    p.extendL = c * (search + 1) * 0.65 + r * beat.second * 2;
    p.extendR = c * (1 - search) * 0.65 + h * 0.8 + r * beat.gesture * 2;
    p.footL = r * beat.second * 0.12; p.footR = r * beat.gesture * 0.12;
    p.glow = r * beat.gesture;
    p.scaleX += f * (done.prepare * 0.055 - done.jump * 0.018 + done.land * 0.035);
    p.scaleY += f * (-done.prepare * 0.075 + done.jump * 0.04 - done.land * 0.045);
    p.y += f * (done.prepare * 2.5 - done.jump * 7.8 + done.land * 2.2);
    p.armL -= f * done.wave * 1.75; p.armR -= f * done.wave * 1.75;
    p.antenna += f * (done.jump * 0.42 + done.settle * 0.53);
    p.rotation += f * done.settle * 0.035;
  } else if (id === 'jiujiu') {
    const flap = Math.pow(Math.max(0, sin(t, 0.78)), 0.65);
    const figure = t * TAU / 5.8;
    p.x += c * sin(t, 2.7) * 2.6 + h * Math.sin(figure) * 3.2 + r * beat.gesture * 1.7;
    p.y += sin(t, 3.1) * 0.8 - c * flap * 1.15 + h * Math.sin(figure * 2) * 1.35 - r * beat.stress * 1.9;
    p.rotation += c * sin(t, 2.7, -0.5) * 0.07 + h * Math.cos(figure) * 0.06 - r * beat.gesture * 0.065;
    p.gazeX += c * sin(t, 3.7) * 2.1 + h * eye.x * 2.7 + r * 0.65;
    p.gazeY += -h * 1.9 + c * sin(t, 4.7) * 0.5;
    p.wingL = c * (0.17 - flap * 0.62) + h * (sin(t, 2.8) * 0.12 - 0.08) - r * beat.second * 0.72;
    p.wingR = c * (0.17 - Math.pow(Math.max(0, sin(t, 0.78, -0.55)), 0.65) * 0.62) + h * (sin(t, 2.8, -1.1) * 0.15 - 0.06) - r * beat.gesture * 0.82;
    p.tail = c * sin(t, 1.56, -0.65) * 0.28 + h * Math.cos(figure - 0.65) * 0.24 + r * sin(t, 1.2, 0.4) * 0.18;
    p.armL = p.wingL; p.armR = p.wingR;
    p.glow = r * beat.gesture;
    p.scaleX += f * (done.prepare * -0.035 + done.jump * 0.035);
    p.scaleY += f * (done.prepare * 0.025 - done.jump * 0.025);
    p.y += f * (done.prepare * 1.5 - done.jump * 7.8 + done.settle * 0.65);
    p.x += f * Math.sin(doneAge * 3.7) * done.jump * 2.1;
    p.rotation += f * (done.jump * 0.11 + done.settle * 0.065);
    p.wingL -= f * done.wave * 1.07; p.wingR -= f * done.wave * 0.8;
    p.tail += f * (done.jump * -0.3 + done.settle * 0.35);
  } else {
    const patrol = mod(t, 4.8); const stepL = pulse(patrol, 0.12, 0.36, 0.62); const stepR = pulse(patrol, 0.75, 1, 1.28);
    const pace = mod(t, 5.2); const thinkL = pulse(pace, 0.3, 0.6, 0.9); const thinkR = pulse(pace, 1.05, 1.35, 1.65);
    p.x += c * ((stepL - stepR) * 1.6) + h * ((thinkL - thinkR) * 1.2);
    p.y -= c * (stepL + stepR) * 1.4 + h * (thinkL + thinkR) * 0.8 + r * beat.stress * 0.85;
    p.rotation += c * (stepL - stepR) * 0.055 + h * (0.04 + (thinkL - thinkR) * 0.03);
    p.gazeX += c * (patrol < 2 ? -1.8 : 1.8) + h * Math.round(eye.x * 1.5) * 1.3 + r * sin(t, 3.7) * 0.5;
    p.gazeY -= h * 1.6;
    p.footL = c * stepL * 1.1 + h * thinkL * 0.8 + r * beat.second * 0.22;
    p.footR = c * stepR * 1.1 + h * thinkR * 0.8 + r * beat.gesture * 0.22;
    p.armL = c * (stepR * 0.55 - stepL * 0.25) + h * 0.05 - r * beat.second * 0.62;
    p.armR = c * (-stepR * 0.25 + stepL * 0.55 - pulse(patrol, 2.3, 2.65, 3.25) * 1.25) - h * (2.5 + sin(t, 3.8) * 0.035) - r * beat.gesture * 0.7;
    p.antenna = c * (stepL - stepR) * 0.3 + r * beat.stress * 0.2;
    p.glow = r * beat.gesture;
    const cheerL = pulse(doneAge, 0.22, 0.43, 0.72); const cheerR = pulse(doneAge, 0.67, 0.89, 1.17);
    p.y += f * (done.prepare * 2.2 - (cheerL + cheerR) * 3.6 + done.land * 0.7);
    p.scaleY -= f * done.prepare * 0.065; p.scaleX += f * done.prepare * 0.045;
    p.armL -= f * cheerL * 1.7; p.armR -= f * cheerR * 1.7;
    p.footL += f * cheerL * 0.8; p.footR += f * cheerR * 0.8;
    p.rotation += f * (cheerL - cheerR) * 0.075;
  }
  p.scaleX += touch * 0.05; p.scaleY -= touch * 0.065; p.y += touch * 1.7;
  p.armL -= touch * 0.18; p.armR -= touch * 0.18;
  p.gazeY -= touch * 0.65;
  if (Number(options.size || 100) <= 48) { p.x *= 0.6; p.y *= 0.6; p.rotation *= 0.76; }
  return p;
}

function colors(id, { mono, dark, size = 100 }) {
  const palettes = {
    tuantuan: ['#FFF0E6', '#F0B29A', '#C97762', '#E9A08C'],
    fangbo: ['#EAF4ED', '#98C8B7', '#4C8F81', '#A8CBBB'],
    jiujiu: ['#FFF5D7', '#EDCD78', '#B89134', '#E8B964'],
    zhedou: ['#EEF1DC', '#BBCB8A', '#7C904C', '#BECC92'],
  };
  const p = palettes[id] || palettes.tuantuan;
  if (mono) return {
    ink: dark ? '#EDF0E9' : '#303D3A', fill: dark ? '#2D3837' : '#F5F5EF',
    accent: dark ? '#6D8580' : '#C8D0C8', deep: dark ? '#C7D3CB' : '#526B62',
    blush: dark ? '#6C7975' : '#C0CCC3', visor: dark ? '#394B49' : '#E3E7E0', shadow: dark ? '#070D0C' : '#54645C', small: size <= 36,
  };
  return {
    ink: dark ? '#E8EEE5' : '#33413D', fill: dark ? { tuantuan: '#594B43', fangbo: '#334E46', jiujiu: '#584E32', zhedou: '#454D32' }[id] || '#394A41' : p[0],
    accent: dark ? { tuantuan: '#BE8C75', fangbo: '#69A58F', jiujiu: '#BDA056', zhedou: '#94A567' }[id] || p[1] : p[1],
    deep: dark ? p[1] : p[2], blush: p[3], visor: dark ? '#263B36' : '#F8FBF2', shadow: dark ? '#070D0C' : '#6C776B', small: size <= 36,
  };
}

function path(ctx, commands, fill, stroke, width = 2.1) {
  ctx.beginPath(); commands(ctx);
  if (fill) { ctx.fillStyle = fill; ctx.fill(); }
  if (stroke) { ctx.strokeStyle = stroke; ctx.lineWidth = width * (ctx[STROKE_SCALE] || 1); ctx.stroke(); }
}
function ellipse(ctx, x, y, rx, ry, fill, stroke, width = 2.1) {
  path(ctx, c => c.ellipse(x, y, Math.max(0.01, rx), Math.max(0.01, ry), 0, 0, TAU), fill, stroke, width);
}
function line(ctx, points, color, width = 2.1) {
  path(ctx, c => { c.moveTo(points[0][0], points[0][1]); points.slice(1).forEach(pt => c.lineTo(pt[0], pt[1])); }, null, color, width);
}
function rr(ctx, x, y, w, h, r, fill, stroke, width = 2.1) {
  path(ctx, c => c.roundRect(x, y, w, h, r), fill, stroke, width);
}
function local(ctx, x, y, angle, draw) {
  ctx.save(); ctx.translate(x, y); ctx.rotate(angle); draw(); ctx.restore();
}
function sparkle(ctx, x, y, size, opacity, color) {
  if (opacity <= 0.005) return;
  ctx.save(); ctx.globalAlpha *= opacity;
  path(ctx, c => {
    c.moveTo(x, y - size); c.quadraticCurveTo(x + size * 0.15, y - size * 0.12, x + size, y);
    c.quadraticCurveTo(x + size * 0.13, y + size * 0.12, x, y + size);
    c.quadraticCurveTo(x - size * 0.15, y + size * 0.12, x - size, y);
    c.quadraticCurveTo(x - size * 0.13, y - size * 0.12, x, y - size); c.closePath();
  }, color, null); ctx.restore();
}
function shadow(ctx, p, c, width = 22) {
  ctx.save(); ctx.globalAlpha = 0.1 * clamp(1 + p.y * 0.025, 0.5, 1.15);
  ellipse(ctx, 50, 88, width + p.y * 0.15, 2.3, c.shadow); ctx.restore();
}
function eye(ctx, x, y, p, c, { squared = false, large = false, smile = 0 } = {}) {
  const ex = x + p.gazeX; const ey = y + p.gazeY;
  const closing = Math.max(p.blink, smile * 0.88);
  if (smile > 0.72 && p.blink < 0.35) {
    path(ctx, q => { q.moveTo(ex - 3.1, ey + 0.8); q.quadraticCurveTo(ex, ey - 3.3, ex + 3.1, ey + 0.8); }, null, c.ink, 2.1);
  } else if (squared) {
    rr(ctx, ex - 2.8, ey - 3.3 * (1 - closing), 5.6, Math.max(1.6, 6.6 * (1 - closing)), 0.7, c.ink);
  } else {
    ellipse(ctx, ex, ey, (large ? 3.25 : 2.65) * (c.small ? 1.16 : 1), Math.max(0.75, (large ? 4.5 : 3.8) * (1 - closing)), c.ink);
    if (large && closing < 0.4 && !c.small) ellipse(ctx, ex - 0.75, ey - 1.1, 0.75, 0.9, c.fill);
  }
}
function roundMouth(ctx, x, y, p, c, tiny = false) {
  if (p.mouth > 0.13) ellipse(ctx, x, y + p.mouth, tiny ? 1.7 + p.mouth : 2 + p.mouth * 1.8, 0.8 + p.mouth * (tiny ? 2.1 : 3), c.ink);
  else path(ctx, q => { q.moveTo(x - 3.2, y); q.quadraticCurveTo(x, y + 3.2 + p.smile * 0.4, x + 3.2, y); }, null, c.ink, 1.8);
}
function cheek(ctx, x, y, c, alpha = 0.5) { ctx.save(); ctx.globalAlpha *= alpha; ellipse(ctx, x, y, 3.3, 1.65, c.blush); ctx.restore(); }
function bodyTransform(ctx, p, draw) {
  local(ctx, 50 + p.x, 52 + p.y, p.rotation, () => {
    ctx.scale(p.scaleX, p.scaleY); ctx.translate(-50, -52); draw();
  });
}

function drawTuan(ctx, p, c) {
  shadow(ctx, p, c, 21);
  bodyTransform(ctx, p, () => {
    local(ctx, 38, 75, p.footL * 0.5, () => ellipse(ctx, -1, 3 - p.footL * 3, 6.4, 4.2, c.accent, c.ink));
    local(ctx, 62, 75, -p.footR * 0.5, () => ellipse(ctx, 1, 3 - p.footR * 3, 6.4, 4.2, c.accent, c.ink));
    // The silhouette has a shallow tuft and a weighted belly rather than a perfect circle.
    path(ctx, q => {
      q.moveTo(47, 23); q.bezierCurveTo(30, 22, 24, 35, 24, 51);
      q.bezierCurveTo(23, 70, 31, 78, 50, 78); q.bezierCurveTo(69, 78, 77, 69, 76, 51);
      q.bezierCurveTo(76, 34, 67, 24, 54, 24);
      q.quadraticCurveTo(53, 18, 48, 18); q.quadraticCurveTo(50, 21, 47, 23); q.closePath();
    }, c.fill, c.ink, 2.2);
    local(ctx, 26, 53, -p.armL, () => path(ctx, q => {
      q.moveTo(1, -3); q.bezierCurveTo(-9, -7, -14, -3, -10, 2); q.quadraticCurveTo(-4, 8, 1, 4);
    }, c.accent, c.ink, 2));
    local(ctx, 74, 53, p.armR, () => path(ctx, q => {
      const h = p.thinking;
      q.moveTo(-1, -3);
      q.bezierCurveTo(9 - h * 14, -7 - h, 14 - h * 24, -3 - h * 4, 10 - h * 20, 2 - h * 5);
      // Matching tangents keep the tucked tip round throughout the state blend.
      q.bezierCurveTo(6 - h * 16, 7 - h * 6, 4 - h * 8, 8 - h * 4, -1, 4);
    }, c.accent, c.ink, 2));
    eye(ctx, 41, 46, p, c, { large: true, smile: p.eyeSmile });
    eye(ctx, 59, 46, p, c, { large: true, smile: p.eyeSmile });
    cheek(ctx, 33, 54, c, 0.6); cheek(ctx, 67, 54, c, 0.6);
    roundMouth(ctx, 50 + p.gazeX * 0.22, 57 + p.gazeY * 0.2, p, c);
    if (p.glow > 0.04) {
      const y = 63 - p.glow * 8;
      sparkle(ctx, 74 + p.glow * 6, y, 3.4, p.glow, c.deep);
    }
  });
  sparkle(ctx, 21, 23, 3.8, p.sparkle, c.deep); sparkle(ctx, 81, 34, 2.8, p.sparkle * 0.85, c.deep);
}

function boxArm(ctx, x, y, side, angle, extend, c) {
  local(ctx, x, y, side * angle, () => {
    line(ctx, [[0, 0], [side * (4.1 + extend), 2.5], [side * (5.3 + extend), 6.8]], c.ink, 2.4);
    ellipse(ctx, side * (5.3 + extend), 7.6, 3, 3.4, c.accent, c.ink, 1.9);
  });
}
function drawFang(ctx, p, c) {
  shadow(ctx, p, c, 20.5);
  bodyTransform(ctx, p, () => {
    local(ctx, 38, 75, p.footL, () => rr(ctx, -5.2, 0, 10.4, 6.6, 2.5, c.accent, c.ink, 2));
    local(ctx, 62, 75, -p.footR, () => rr(ctx, -5.2, 0, 10.4, 6.6, 2.5, c.accent, c.ink, 2));
    boxArm(ctx, 26, 51, -1, p.armL, p.extendL, c); boxArm(ctx, 74, 51, 1, p.armR, p.extendR, c);
    // The spring is genuinely articulated; its tip lags behind the box during movement.
    local(ctx, 51, 30, p.antenna * 0.65, () => {
      path(ctx, q => { q.moveTo(0, 0); q.lineTo(0, -3); q.lineTo(-3, -5); q.lineTo(3, -7); q.lineTo(-2, -9); q.lineTo(1, -11); }, null, c.ink, 1.8);
      ellipse(ctx, 1, -13, 3.1, 3.1, c.accent, c.ink, 1.8);
    });
    rr(ctx, 26, 28, 48, 47, [11, 11, 12, 12], c.fill, c.ink, 2.2);
    // Cream face inset and offset little corner dent make this a toy, not a generic badge.
    rr(ctx, 31.5, 35.5, 37, 25, 7, c.visor, c.ink, 1.55);
    eye(ctx, 41, 45, p, c, { smile: p.eyeSmile }); eye(ctx, 59, 45, p, c, { smile: p.eyeSmile });
    if (p.mouth > 0.16) rr(ctx, 47 - p.mouth, 53, 6 + p.mouth * 2, 1.4 + p.mouth * 2.7, 1.1, c.ink);
    else path(ctx, q => { q.moveTo(47.3, 53.5); q.quadraticCurveTo(50, 55.8 + p.smile * 0.5, 52.7, 53.5); }, null, c.ink, 1.55);
    rr(ctx, 35, 66, 12, 2.7, 1.3, c.accent); if (!c.small) ellipse(ctx, 63, 67.4, 1.6, 1.6, c.deep);
    if (p.glow > 0.04) {
      ctx.save(); ctx.globalAlpha *= p.glow;
      rr(ctx, 75 + p.glow * 4, 43 - p.glow * 2, 6, 4.7, 1.4, c.accent, c.ink, 1.1); ctx.restore();
    }
  });
  sparkle(ctx, 19, 30, 3.3, p.sparkle, c.deep); sparkle(ctx, 81, 23, 2.5, p.sparkle * 0.85, c.deep);
}

function wing(ctx, x, y, side, angle, c) {
  local(ctx, x, y, side * angle, () => path(ctx, q => {
    q.moveTo(0, -3); q.bezierCurveTo(side * 8, -6, side * 15, 0, side * 9, 4);
    q.quadraticCurveTo(side * 4, 6, 0, 3); q.closePath();
  }, c.accent, c.ink, 1.9));
}
function drawJiu(ctx, p, c) {
  shadow(ctx, { ...p, y: p.y - 7 }, c, 19);
  bodyTransform(ctx, p, () => {
    // Ribbon tail is behind the hull and bends around a separate pivot.
    local(ctx, 26, 49, p.tail, () => path(ctx, q => {
      q.moveTo(3, -7); q.lineTo(-7, -10); q.quadraticCurveTo(-9, -11, -8, -6);
      q.lineTo(-4, 0); q.lineTo(-8, 6); q.quadraticCurveTo(-9, 11, -7, 10); q.lineTo(3, 7); q.closePath();
    }, c.accent, c.ink, 2));
    wing(ctx, 44, 61, -1, p.wingL, c);
    path(ctx, q => {
      q.moveTo(39, 31); q.lineTo(64, 31); q.bezierCurveTo(79, 31, 83, 39, 83, 49);
      q.bezierCurveTo(83, 61, 76, 67, 62, 67); q.lineTo(38, 67);
      q.bezierCurveTo(26, 67, 21, 59, 21, 49); q.bezierCurveTo(21, 38, 27, 31, 39, 31); q.closePath();
    }, c.fill, c.ink, 2.2);
    // Off-centre visor makes the direction of this hovering messenger unmistakable.
    rr(ctx, 39, 37, 35, 23, [9, 10, 10, 9], c.visor, null);
    eye(ctx, 47, 46.5, p, c, { smile: p.eyeSmile }); eye(ctx, 63, 46.5, p, c, { smile: p.eyeSmile });
    if (p.mouth > 0.15) {
      path(ctx, q => { q.moveTo(53.5, 54); q.quadraticCurveTo(56, 53.2 - p.mouth * 0.3, 59, 54); q.lineTo(56.2, 56 + p.mouth * 2.3); q.closePath(); }, c.deep, null);
    } else path(ctx, q => { q.moveTo(53.6, 54.1); q.quadraticCurveTo(56.2, 56.7, 58.7, 54.1); }, null, c.ink, 1.6);
    // Tiny navigation seams stay secondary to the eyes at 26 px.
    if (!c.small) { line(ctx, [[29.5, 43], [33, 41.5]], c.deep, 1.5); line(ctx, [[29.5, 48], [33, 47.5]], c.deep, 1.5); }
    wing(ctx, 65, 62, 1, p.wingR, c);
    if (p.glow > 0.04) sparkle(ctx, 84 + p.glow * 2, 45 - p.glow * 3, 2.8, p.glow, c.deep);
  });
  sparkle(ctx, 29, 20, 3.2, p.sparkle, c.deep); sparkle(ctx, 82, 27, 2.9, p.sparkle * 0.8, c.deep);
}

function pixelArm(ctx, x, y, side, angle, c) {
  local(ctx, x, y, side * angle, () => {
    line(ctx, [[0, 0], [side * 4.8, 0], [side * 4.8, 7], [side * 7.2, 7]], c.ink, 2.5);
    rr(ctx, side < 0 ? -10 : 4.4, 4.5, 5.6, 5.6, 1.1, c.accent, c.ink, 1.8);
  });
}
function drawZhe(ctx, p, c) {
  shadow(ctx, p, c, 19.5);
  bodyTransform(ctx, p, () => {
    local(ctx, 38, 73, p.footL * 0.45, () => {
      line(ctx, [[0, 0], [0, 5 - p.footL * 2]], c.ink, 2.5);
      rr(ctx, -6, 5 - p.footL * 2, 11.5, 5.5, 1.3, c.accent, c.ink, 1.9);
    });
    local(ctx, 62, 73, -p.footR * 0.45, () => {
      line(ctx, [[0, 0], [0, 5 - p.footR * 2]], c.ink, 2.5);
      rr(ctx, -5.5, 5 - p.footR * 2, 11.5, 5.5, 1.3, c.accent, c.ink, 1.9);
    });
    pixelArm(ctx, 27, 50, -1, p.armL, c); pixelArm(ctx, 73, 50, 1, p.armR, c);
    // A stepped top corner and a single off-axis aerial form a recognisable silhouette.
    local(ctx, 59, 29, p.antenna * 0.22, () => {
      line(ctx, [[0, 0], [0, -8], [5, -8]], c.ink, 2);
      rr(ctx, 3.5, -10.7, 5, 5, 0.75, c.accent, c.ink, 1.65);
    });
    path(ctx, q => {
      q.moveTo(32, 29); q.lineTo(58, 29); q.lineTo(58, 33); q.lineTo(68, 33);
      q.quadraticCurveTo(73, 33, 73, 38); q.lineTo(73, 69); q.quadraticCurveTo(73, 74, 68, 74);
      q.lineTo(32, 74); q.quadraticCurveTo(27, 74, 27, 69); q.lineTo(27, 34); q.quadraticCurveTo(27, 29, 32, 29); q.closePath();
    }, c.fill, c.ink, 2.2);
    rr(ctx, 32, 37, 36, 24, 3, c.visor);
    eye(ctx, 41, 46, p, c, { squared: true, smile: p.eyeSmile });
    eye(ctx, 59, 47.5, p, c, { squared: true, smile: p.eyeSmile });
    if (p.mouth > 0.2) {
      rr(ctx, 46.5, 54, 7, 1.9 + p.mouth * 2.8, 0.5, c.ink);
    } else line(ctx, [[46.7, 54], [46.7, 55.8], [53.3, 55.8], [53.3, 54]], c.ink, 1.6);
    rr(ctx, 35, 66, 4, 3, 0.6, c.accent); rr(ctx, 42, 66, 4, 3, 0.6, c.accent);
    if (!c.small) line(ctx, [[59, 66], [64, 66]], c.deep, 1.6);
    if (p.glow > 0.05) {
      ctx.save(); ctx.globalAlpha *= p.glow;
      rr(ctx, 79 + p.glow * 4, 43 - p.glow * 4, 4, 4, 0.4, c.accent, c.ink, 0.9); ctx.restore();
    }
  });
  sparkle(ctx, 19, 23, 3.1, p.sparkle, c.deep); sparkle(ctx, 82, 33, 2.9, p.sparkle * 0.9, c.deep);
}

/** Paint one character into an already scaled, cleared 100 × 100 canvas. */
function drawBot(ctx, id, options = {}) {
  const valid = BOT_CATALOG.some(bot => bot.id === id) ? id : BOT_CATALOG[0].id;
  const p = samplePose(valid, options); const c = colors(valid, options);
  const previousScale = ctx[STROKE_SCALE];
  ctx[STROKE_SCALE] = mix(2.05, 1, smooth(26, 72, Number(options.size || 100)));
  ctx.save(); ctx.lineCap = 'round'; ctx.lineJoin = 'round';
  if (valid === 'tuantuan') drawTuan(ctx, p, c);
  else if (valid === 'fangbo') drawFang(ctx, p, c);
  else if (valid === 'jiujiu') drawJiu(ctx, p, c);
  else drawZhe(ctx, p, c);
  ctx.restore();
  ctx[STROKE_SCALE] = previousScale;
  return p;
}

globalThis.BotRigs = Object.freeze({ BOT_CATALOG, drawBot, samplePose });
})();
