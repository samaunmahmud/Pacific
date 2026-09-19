import type { ReactNode } from 'react';
import type { DemoArtKind } from './demoArtKinds';

/** The four colours every icon is painted with, derived from one hue so each product gets its own look. */
interface Ink {
  m: string; // main
  l: string; // light accent
  d: string; // dark accent
  w: string; // white-ish
}

const inkFor = (hue: number): Ink => ({
  m: `hsl(${hue} 52% 42%)`,
  l: `hsl(${hue} 70% 82%)`,
  d: `hsl(${hue} 45% 22%)`,
  w: '#ffffff',
});

const stroke = (color: string, width: number) => ({ stroke: color, strokeWidth: width, fill: 'none', strokeLinecap: 'round' as const, strokeLinejoin: 'round' as const });

/** Wraps a title onto at most three short lines for the book cover. */
function wrap(text: string, max = 11): string[] {
  const words = text.replace(/\s*\(.*\)$/, '').split(' ');
  const lines: string[] = [];
  let line = '';
  for (const w of words) {
    if ((line + ' ' + w).trim().length > max && line) {
      lines.push(line);
      line = w;
    } else line = (line + ' ' + w).trim();
  }
  if (line) lines.push(line);
  return lines.slice(0, 4);
}

type Draw = (c: Ink, label: string) => ReactNode;

const ICONS: Record<DemoArtKind, Draw> = {
  headphones: (c) => (
    <>
      <path d="M22 58a28 28 0 0 1 56 0" {...stroke(c.d, 6)} />
      <rect x="15" y="54" width="16" height="26" rx="7" fill={c.m} />
      <rect x="69" y="54" width="16" height="26" rx="7" fill={c.m} />
      <rect x="20" y="58" width="6" height="18" rx="3" fill={c.l} />
      <rect x="74" y="58" width="6" height="18" rx="3" fill={c.l} />
    </>
  ),
  earbuds: (c) => (
    <>
      <circle cx="35" cy="40" r="12" fill={c.m} />
      <rect x="40" y="46" width="8" height="30" rx="4" fill={c.d} />
      <circle cx="65" cy="40" r="12" fill={c.m} />
      <rect x="52" y="46" width="8" height="30" rx="4" fill={c.d} />
      <circle cx="35" cy="40" r="5" fill={c.l} />
      <circle cx="65" cy="40" r="5" fill={c.l} />
    </>
  ),
  speaker: (c) => (
    <>
      <rect x="28" y="16" width="44" height="68" rx="9" fill={c.m} />
      <circle cx="50" cy="60" r="16" fill={c.d} />
      <circle cx="50" cy="60" r="9" fill={c.l} />
      <circle cx="50" cy="60" r="3" fill={c.d} />
      <circle cx="50" cy="33" r="7" fill={c.d} />
      <circle cx="50" cy="33" r="3" fill={c.l} />
    </>
  ),
  microphone: (c) => (
    <>
      <rect x="40" y="14" width="20" height="38" rx="10" fill={c.m} />
      <rect x="43" y="22" width="14" height="2.5" fill={c.l} />
      <rect x="43" y="28" width="14" height="2.5" fill={c.l} />
      <rect x="43" y="34" width="14" height="2.5" fill={c.l} />
      <path d="M31 46a19 19 0 0 0 38 0" {...stroke(c.d, 4)} />
      <path d="M50 65v13M37 80h26" {...stroke(c.d, 4)} />
    </>
  ),
  keyboard: (c) => (
    <>
      <rect x="10" y="32" width="80" height="40" rx="6" fill={c.m} />
      {[0, 1, 2, 3, 4, 5].map((i) => <rect key={`a${i}`} x={16 + i * 12} y="38" width="9" height="8" rx="2" fill={c.l} />)}
      {[0, 1, 2, 3, 4, 5].map((i) => <rect key={`b${i}`} x={19 + i * 12} y="49" width="9" height="8" rx="2" fill={c.l} />)}
      <rect x="28" y="60" width="44" height="7" rx="2" fill={c.l} />
    </>
  ),
  mouse: (c) => (
    <>
      <rect x="33" y="18" width="34" height="62" rx="17" fill={c.m} />
      <rect x="49" y="18" width="2" height="24" fill={c.d} />
      <rect x="46.5" y="28" width="7" height="11" rx="3.5" fill={c.l} />
      <path d="M33 44h34" {...stroke(c.d, 2)} />
    </>
  ),
  webcam: (c) => (
    <>
      <circle cx="50" cy="40" r="24" fill={c.m} />
      <circle cx="50" cy="40" r="15" fill={c.d} />
      <circle cx="50" cy="40" r="8" fill={c.l} />
      <circle cx="45" cy="35" r="2.5" fill={c.w} />
      <rect x="44" y="62" width="12" height="12" fill={c.d} />
      <rect x="30" y="74" width="40" height="7" rx="3.5" fill={c.m} />
    </>
  ),
  monitor: (c) => (
    <>
      <rect x="12" y="20" width="76" height="52" rx="5" fill={c.d} />
      <rect x="17" y="25" width="66" height="42" rx="2" fill={c.l} />
      <path d="M17 60l18-16 12 10 14-14 22 16v11H17z" fill={c.m} opacity="0.55" />
      <rect x="45" y="72" width="10" height="9" fill={c.m} />
      <rect x="32" y="80" width="36" height="6" rx="3" fill={c.m} />
    </>
  ),
  laptop: (c) => (
    <>
      <rect x="22" y="22" width="56" height="40" rx="4" fill={c.d} />
      <rect x="26" y="26" width="48" height="32" rx="1.5" fill={c.l} />
      <path d="M26 54l14-12 10 8 10-10 14 14z" fill={c.m} opacity="0.55" />
      <path d="M10 66h80l-6 10H16z" fill={c.m} />
      <rect x="42" y="66" width="16" height="3" rx="1.5" fill={c.d} />
    </>
  ),
  cable: (c) => (
    <>
      <path d="M22 72C22 40 78 66 78 32" {...stroke(c.m, 5)} />
      <rect x="12" y="66" width="18" height="12" rx="3" fill={c.d} />
      <rect x="16" y="62" width="10" height="5" fill={c.l} />
      <rect x="70" y="22" width="18" height="12" rx="3" fill={c.d} />
      <rect x="74" y="33" width="10" height="5" fill={c.l} />
    </>
  ),
  charger: (c) => (
    <>
      <rect x="33" y="24" width="34" height="48" rx="7" fill={c.m} />
      <rect x="40" y="12" width="6" height="12" fill={c.d} />
      <rect x="54" y="12" width="6" height="12" fill={c.d} />
      <rect x="41" y="54" width="18" height="9" rx="4" fill={c.d} />
      <path d="M52 32l-8 12h6l-2 8 9-13h-6z" fill={c.l} />
    </>
  ),
  battery: (c) => (
    <>
      <rect x="24" y="24" width="52" height="54" rx="9" fill={c.m} />
      <rect x="38" y="33" width="24" height="7" rx="3" fill={c.d} />
      {[0, 1, 2, 3].map((i) => <circle key={i} cx={36 + i * 10} cy="60" r="3.5" fill={i < 3 ? c.l : c.d} />)}
    </>
  ),
  chip: (c) => (
    <>
      {[0, 1, 2, 3].map((i) => (
        <g key={i} fill={c.l}>
          <rect x={35 + i * 10} y="20" width="4" height="10" />
          <rect x={35 + i * 10} y="70" width="4" height="10" />
          <rect x="20" y={35 + i * 10} width="10" height="4" />
          <rect x="70" y={35 + i * 10} width="10" height="4" />
        </g>
      ))}
      <rect x="28" y="28" width="44" height="44" rx="5" fill={c.d} />
      <rect x="36" y="36" width="28" height="28" rx="3" fill={c.m} />
      <circle cx="42" cy="42" r="2.5" fill={c.l} />
    </>
  ),
  fan: (c) => (
    <>
      <rect x="14" y="14" width="72" height="72" rx="10" fill={c.d} />
      <circle cx="50" cy="50" r="31" fill={c.l} />
      {[0, 90, 180, 270].map((a) => <ellipse key={a} cx="50" cy="33" rx="9" ry="15" fill={c.m} transform={`rotate(${a + 20} 50 50)`} />)}
      <circle cx="50" cy="50" r="7" fill={c.d} />
    </>
  ),
  chair: (c) => (
    <>
      <rect x="32" y="14" width="36" height="38" rx="10" fill={c.m} />
      <rect x="37" y="20" width="26" height="26" rx="6" fill={c.l} opacity="0.6" />
      <rect x="28" y="54" width="44" height="11" rx="5.5" fill={c.d} />
      <rect x="47" y="65" width="6" height="12" fill={c.d} />
      <path d="M28 82h44" {...stroke(c.d, 5)} />
      {[28, 50, 72].map((x) => <circle key={x} cx={x} cy="86" r="3.5" fill={c.m} />)}
    </>
  ),
  desk: (c) => (
    <>
      <rect x="10" y="34" width="80" height="9" rx="3" fill={c.m} />
      <rect x="16" y="43" width="6" height="38" fill={c.d} />
      <rect x="78" y="43" width="6" height="38" fill={c.d} />
      <rect x="52" y="43" width="26" height="16" rx="2" fill={c.l} />
      <circle cx="65" cy="51" r="2" fill={c.d} />
      <rect x="22" y="20" width="22" height="14" rx="2" fill={c.d} />
    </>
  ),
  lamp: (c) => (
    <>
      <rect x="30" y="78" width="36" height="7" rx="3.5" fill={c.d} />
      <path d="M48 78L36 48 60 30" {...stroke(c.d, 4.5)} />
      <path d="M52 22l24 12-9 14-22-12z" fill={c.m} />
      <path d="M62 44l14 12-22 4z" fill={c.l} opacity="0.7" />
    </>
  ),
  camera: (c) => (
    <>
      <rect x="12" y="32" width="76" height="46" rx="9" fill={c.m} />
      <rect x="30" y="23" width="26" height="11" rx="3" fill={c.d} />
      <circle cx="50" cy="56" r="19" fill={c.d} />
      <circle cx="50" cy="56" r="13" fill={c.l} />
      <circle cx="50" cy="56" r="6" fill={c.d} />
      <circle cx="46" cy="52" r="2.5" fill={c.w} />
      <rect x="70" y="38" width="11" height="6" rx="2" fill={c.l} />
    </>
  ),
  tripod: (c) => (
    <>
      <path d="M50 34L28 84M50 34V84M50 34l22 50" {...stroke(c.d, 4)} />
      <rect x="47" y="24" width="6" height="14" fill={c.d} />
      <rect x="36" y="14" width="28" height="12" rx="4" fill={c.m} />
      <circle cx="58" cy="20" r="2.5" fill={c.l} />
    </>
  ),
  backpack: (c) => (
    <>
      <path d="M41 24v-6a9 9 0 0 1 18 0v6" {...stroke(c.d, 4)} />
      <rect x="24" y="24" width="52" height="60" rx="17" fill={c.m} />
      <rect x="33" y="52" width="34" height="24" rx="9" fill={c.d} opacity="0.85" />
      <path d="M35 60h30" {...stroke(c.l, 2.5)} />
      <rect x="44" y="36" width="12" height="7" rx="3" fill={c.l} />
    </>
  ),
  bottle: (c) => (
    <>
      <rect x="39" y="12" width="22" height="11" rx="3" fill={c.d} />
      <rect x="43" y="23" width="14" height="8" fill={c.m} />
      <rect x="30" y="30" width="40" height="56" rx="12" fill={c.m} />
      <rect x="30" y="48" width="40" height="16" fill={c.l} />
      <rect x="37" y="36" width="4" height="40" rx="2" fill={c.w} opacity="0.35" />
    </>
  ),
  mug: (c) => (
    <>
      <path d="M66 44h5a10 10 0 0 1 0 22h-5" {...stroke(c.m, 6)} />
      <rect x="24" y="34" width="44" height="46" rx="9" fill={c.m} />
      <rect x="30" y="40" width="6" height="34" rx="3" fill={c.w} opacity="0.3" />
      <path d="M38 26q5-6 0-12M52 26q5-6 0-12" {...stroke(c.d, 3)} />
    </>
  ),
  watch: (c) => (
    <>
      <rect x="39" y="10" width="22" height="80" rx="7" fill={c.d} />
      <circle cx="50" cy="50" r="24" fill={c.m} />
      <circle cx="50" cy="50" r="18.5" fill={c.l} />
      <path d="M50 50V38M50 50l9 5" {...stroke(c.d, 3.5)} />
      <circle cx="50" cy="50" r="2.5" fill={c.d} />
    </>
  ),
  sunglasses: (c) => (
    <>
      <path d="M12 42l-4-6M88 42l4-6" {...stroke(c.d, 4)} />
      <rect x="12" y="38" width="34" height="26" rx="12" fill={c.d} />
      <rect x="54" y="38" width="34" height="26" rx="12" fill={c.d} />
      <path d="M46 46q4-5 8 0" {...stroke(c.d, 4)} />
      <path d="M19 45l8-3M61 45l8-3" {...stroke(c.l, 3)} />
    </>
  ),
  book: (c, label) => {
    const lines = wrap(label);
    // long words get a smaller size so they stay inside the cover
    const longest = Math.max(...lines.map((l) => l.length));
    const size = longest > 12 ? 5.2 : longest > 10 ? 6 : 7;
    return (
      <>
        <rect x="22" y="12" width="56" height="76" rx="4" fill={c.m} />
        <rect x="22" y="12" width="9" height="76" rx="3" fill={c.d} />
        <rect x="36" y="20" width="36" height="1.6" fill={c.l} opacity="0.8" />
        {lines.map((line, i) => (
          <text key={i} x="54" y={34 + i * (size + 2)} textAnchor="middle" fontSize={size} fontWeight="700" fontFamily="Georgia, serif" fill={c.w}>{line}</text>
        ))}
        <rect x="36" y="74" width="36" height="1.6" fill={c.l} opacity="0.8" />
        <circle cx="54" cy="81" r="2.6" fill={c.l} />
      </>
    );
  },
  kettle: (c) => (
    <>
      <path d="M28 46q-16 8 0 26" {...stroke(c.d, 5)} />
      <path d="M68 46l16-12v10L68 58z" fill={c.m} />
      <rect x="28" y="36" width="42" height="46" rx="11" fill={c.m} />
      <rect x="33" y="28" width="32" height="9" rx="4.5" fill={c.d} />
      <circle cx="49" cy="25" r="3.5" fill={c.l} />
      <rect x="35" y="44" width="5" height="30" rx="2.5" fill={c.w} opacity="0.3" />
      <rect x="42" y="70" width="14" height="4" rx="2" fill={c.l} />
    </>
  ),
  blender: (c) => (
    <>
      <path d="M31 18h38l-6 54H37z" fill={c.l} stroke={c.m} strokeWidth="3" strokeLinejoin="round" />
      <path d="M35 52h30l-2 20H37z" fill={c.m} opacity="0.5" />
      <rect x="28" y="11" width="44" height="8" rx="4" fill={c.d} />
      <rect x="30" y="72" width="40" height="16" rx="5" fill={c.m} />
      <circle cx="50" cy="80" r="3.5" fill={c.l} />
    </>
  ),
  pan: (c) => (
    <>
      <rect x="62" y="48" width="32" height="9" rx="4.5" fill={c.d} />
      <circle cx="40" cy="52" r="30" fill={c.d} />
      <circle cx="40" cy="52" r="24" fill={c.m} />
      <path d="M26 44a16 16 0 0 1 12-10" {...stroke(c.l, 3)} />
      <circle cx="90" cy="52.5" r="2" fill={c.l} />
    </>
  ),
  jar: (c) => (
    <>
      <rect x="30" y="20" width="40" height="14" rx="5" fill={c.d} />
      <rect x="26" y="33" width="48" height="50" rx="12" fill={c.m} />
      <rect x="33" y="47" width="34" height="22" rx="5" fill={c.l} />
      <path d="M40 55h20M40 61h13" {...stroke(c.d, 2.5)} />
    </>
  ),
  shirt: (c) => (
    <>
      <path d="M36 18L20 28 10 46l15 7 7-9v42h36V44l7 9 15-7-10-18-16-10q-14 12-28 0z" fill={c.m} />
      <path d="M36 18q14 12 28 0" {...stroke(c.d, 3)} />
      <path d="M32 44v42h36V44" fill="none" stroke={c.l} strokeWidth="1.5" opacity="0.5" />
    </>
  ),
  shoe: (c) => (
    <>
      <path d="M10 66V48q20 0 28-12l6-6q10 16 32 20 14 3 16 16v8z" fill={c.m} />
      <rect x="8" y="68" width="86" height="10" rx="5" fill={c.l} />
      <path d="M44 32l8 8M52 36l8 8M60 42l8 8" {...stroke(c.l, 3)} />
      <path d="M10 58h26" {...stroke(c.d, 3)} />
    </>
  ),
  dumbbell: (c) => (
    <>
      <rect x="28" y="46" width="44" height="8" fill={c.d} />
      <rect x="10" y="32" width="12" height="36" rx="4" fill={c.m} />
      <rect x="21" y="38" width="8" height="24" rx="3" fill={c.d} />
      <rect x="78" y="32" width="12" height="36" rx="4" fill={c.m} />
      <rect x="71" y="38" width="8" height="24" rx="3" fill={c.d} />
    </>
  ),
  tent: (c) => (
    <>
      <path d="M50 16L90 80H10z" fill={c.m} />
      <path d="M50 44l16 36H34z" fill={c.d} />
      <path d="M50 16v28" {...stroke(c.l, 3)} />
      <rect x="6" y="80" width="88" height="5" rx="2.5" fill={c.l} />
    </>
  ),
  gamepad: (c) => (
    <>
      <rect x="10" y="34" width="80" height="38" rx="19" fill={c.m} />
      <rect x="24" y="48" width="18" height="6.5" rx="1" fill={c.d} />
      <rect x="29.7" y="42.3" width="6.5" height="18" rx="1" fill={c.d} />
      <circle cx="68" cy="46" r="4.5" fill={c.l} />
      <circle cx="78" cy="55" r="4.5" fill={c.l} />
      <circle cx="58" cy="55" r="4.5" fill={c.d} />
      <circle cx="68" cy="63" r="4.5" fill={c.d} />
    </>
  ),
  dice: (c) => (
    <>
      <rect x="22" y="22" width="56" height="56" rx="11" fill={c.m} />
      <rect x="22" y="22" width="56" height="56" rx="11" fill="none" stroke={c.d} strokeWidth="3" />
      {[[36, 36], [64, 36], [50, 50], [36, 64], [64, 64]].map(([x, y]) => <circle key={`${x}${y}`} cx={x} cy={y} r="5" fill={c.w} />)}
    </>
  ),
  dryer: (c) => (
    <>
      <rect x="14" y="26" width="54" height="28" rx="14" fill={c.m} />
      <rect x="64" y="30" width="16" height="20" rx="3" fill={c.d} />
      <path d="M32 52l-6 32a5 5 0 0 0 5 6h9a5 5 0 0 0 5-6l-4-32z" fill={c.d} />
      <path d="M86 34h8M86 40h11M86 46h8" {...stroke(c.l, 3)} />
      <circle cx="30" cy="40" r="4" fill={c.l} />
    </>
  ),
  toothbrush: (c) => (
    <>
      <rect x="42" y="12" width="16" height="28" rx="5" fill={c.l} />
      {[0, 1, 2, 3].map((i) => <rect key={i} x={44 + i * 3.6} y="6" width="2.2" height="9" rx="1" fill={c.d} />)}
      <rect x="44" y="38" width="12" height="14" fill={c.m} />
      <rect x="40" y="50" width="20" height="38" rx="9" fill={c.m} />
      <rect x="47" y="58" width="6" height="14" rx="3" fill={c.l} />
    </>
  ),
  plug: (c) => (
    <>
      <path d="M8 55h12" {...stroke(c.d, 4)} />
      <rect x="18" y="38" width="66" height="34" rx="8" fill={c.m} />
      {[0, 1, 2].map((i) => (
        <g key={i}>
          <rect x={26 + i * 19} y="46" width="14" height="18" rx="3" fill={c.d} />
          <rect x={30 + i * 19} y="51" width="2.6" height="7" fill={c.l} />
          <rect x={35 + i * 19} y="51" width="2.6" height="7" fill={c.l} />
        </g>
      ))}
    </>
  ),
};

/** A drawn product picture: a tinted tile with a flat illustration in the middle. */
export function DemoArt({ kind, hue, label, bare = false }: { kind: DemoArtKind; hue: number; label: string; bare?: boolean }) {
  const ink = inkFor(hue);
  const draw = ICONS[kind];
  const id = `g${hue}${kind}`;
  return (
    <svg className="demo-art" viewBox="0 0 100 100" role="img" aria-label={label} preserveAspectRatio="xMidYMid meet">
      <defs>
        <linearGradient id={id} x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor={`hsl(${hue} 60% 96%)`} />
          <stop offset="1" stopColor={`hsl(${hue} 55% 86%)`} />
        </linearGradient>
      </defs>
      {!bare && <rect width="100" height="100" fill={`url(#${id})`} />}
      {!bare && <circle cx="50" cy="52" r="38" fill={ink.w} opacity="0.5" />}
      {!bare && <ellipse cx="50" cy="90" rx="26" ry="3.5" fill={ink.d} opacity="0.12" />}
      {draw ? draw(ink, label) : null}
    </svg>
  );
}
