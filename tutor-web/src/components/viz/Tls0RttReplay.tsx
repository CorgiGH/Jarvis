import type { ReactNode } from "react";
import { AlgoStepperShell, type Frame } from "./AlgoStepperShell";
import { ACCENT, FONT_FAMILY, INK, PAPER } from "./theme";

type Actor = "client" | "server" | "attacker";
type Message = {
  id: number;
  from: Actor;
  to: Actor;
  label: string;
  encrypted: boolean;
  replay: boolean;
  fillColor?: "ink" | "accent";
};
type Phase = 1 | 2 | 3 | 4;
type TLSState = {
  step: number;
  phase: Phase;
  visibleMessages: Message[];
  highlightedMessageId: number | null;
  message: string;
};

const ALL_MESSAGES: Message[] = [
  { id: 1, from: "client", to: "server", label: "ClientHello + key_share", encrypted: false, replay: false },
  { id: 2, from: "server", to: "client", label: "ServerHello + Certificate + Finished", encrypted: true, replay: false },
  { id: 3, from: "client", to: "server", label: "Finished + AppData (GET /home)", encrypted: true, replay: false },
  { id: 4, from: "server", to: "client", label: "NewSessionTicket (PSK)", encrypted: true, replay: false, fillColor: "accent" },
  { id: 5, from: "client", to: "server", label: "ClientHello + EarlyData('GET /transfer?amt=100')", encrypted: true, replay: false, fillColor: "accent" },
  { id: 6, from: "server", to: "client", label: "Accept 0-RTT. Process GET /transfer (charge $100).", encrypted: true, replay: false },
  { id: 7, from: "server", to: "client", label: "ServerHello + Finished + Response", encrypted: true, replay: false },
  { id: 8, from: "client", to: "attacker", label: "Attacker on-path: captured msg #5 bytes", encrypted: false, replay: false },
  { id: 9, from: "attacker", to: "server", label: "Re-send msg #5 bytes (REPLAY)", encrypted: true, replay: true, fillColor: "accent" },
  { id: 10, from: "server", to: "attacker", label: "Accept 0-RTT. Process GET /transfer AGAIN (charge $100 twice!).", encrypted: true, replay: true },
];

export const FRAME_COUNT = 15;

function buildFrames(): Frame<TLSState>[] {
  const frames: Frame<TLSState>[] = [];
  const visible: Message[] = [];

  const mk = (
    step: number,
    phase: Phase,
    highlightedMessageId: number | null,
    message: string,
  ): Frame<TLSState> => ({
    state: {
      step,
      phase,
      visibleMessages: visible.map((m) => ({ ...m })),
      highlightedMessageId,
      message,
    },
    aria: message,
  });

  // Phase 1: initial 1-RTT
  visible.push(ALL_MESSAGES[0]);
  frames.push(mk(0, 1, 1, "Phase 1: Initial 1-RTT handshake. Client → Server: ClientHello with key_share."));
  visible.push(ALL_MESSAGES[1]);
  frames.push(mk(1, 1, 2, "Server → Client: ServerHello + Certificate + Finished. Both have established key material."));
  visible.push(ALL_MESSAGES[2]);
  frames.push(mk(2, 1, 3, "Client → Server: Finished + first Application Data (GET /home)."));
  visible.push(ALL_MESSAGES[3]);
  frames.push(mk(3, 1, 4, "Server → Client: NewSessionTicket. PSK established for future resumption."));

  // Phase 2: 0-RTT resumption (the vulnerable design)
  visible.push(ALL_MESSAGES[4]);
  frames.push(mk(4, 2, 5, "Phase 2: Later. Client RESUMES with 0-RTT. ClientHello + EarlyData together (no extra round-trip)."));
  visible.push(ALL_MESSAGES[5]);
  frames.push(mk(5, 2, 6, "Server: PSK valid, freshness OK → accept 0-RTT. PROCESS the request: charge user $100."));
  visible.push(ALL_MESSAGES[6]);
  frames.push(mk(6, 2, 7, "Server → Client: ServerHello + Finished + Response. Resumption complete."));

  // Phase 3: replay attack
  visible.push(ALL_MESSAGES[7]);
  frames.push(mk(7, 3, 8, "Phase 3: Attacker on-path between client and server. They captured the EarlyData bytes from step 5."));
  visible.push(ALL_MESSAGES[8]);
  frames.push(mk(8, 3, 9, "Attacker → Server: replay the captured ClientHello + EarlyData bytes. Server can't distinguish from legitimate client."));
  visible.push(ALL_MESSAGES[9]);
  frames.push(mk(9, 3, 10, "Server: PSK still valid, replay within freshness window → accept → CHARGE USER $100 AGAIN. Double-payment."));

  // Phase 4: mitigations
  frames.push(mk(10, 4, null, "Phase 4: Mitigations. (1) Server allows 0-RTT ONLY for idempotent operations (GET, not POST/transfer)."));
  frames.push(mk(11, 4, null, "Mitigation (2): Server maintains single-use ticket cache. Same ticket twice → reject the second."));
  frames.push(mk(12, 4, null, "Mitigation (3): Anti-replay window — server tracks recent ticket+nonce pairs (memory cost grows with traffic)."));
  frames.push(mk(13, 4, null, "Trade-off: across a server farm, perfect anti-replay requires shared state. Hard at scale."));
  frames.push(mk(14, 4, null, "CONCLUSION: 0-RTT cuts latency by 1 RTT but trades that for replay risk. Use only for idempotent endpoints OR with strict anti-replay caching."));

  return frames;
}

const FRAMES = buildFrames();

const SVG_W = 480;
const SVG_H = 360;

const COL_W = 100;
const CLIENT_X = 20;
const SERVER_X = 240;
const ATTACKER_X = 360;
const ACTOR_Y = 40;
const ACTOR_LABEL_H = 30;

const MSG_TOP = ACTOR_Y + ACTOR_LABEL_H + 16;
const MSG_BOTTOM = SVG_H - 60;
const MSG_HEIGHT_BUDGET = MSG_BOTTOM - MSG_TOP;

const MSG_Y_FOOTER = 340;

function actorX(a: Actor): number {
  if (a === "client") return CLIENT_X + COL_W / 2;
  if (a === "server") return SERVER_X + COL_W / 2;
  return ATTACKER_X + COL_W / 2;
}

function renderFrame(frame: Frame<TLSState>): ReactNode {
  const { phase, visibleMessages, highlightedMessageId, message } = frame.state;
  const showAttacker = phase >= 3;

  // Layout messages vertically by appearance order
  const slotCount = Math.max(visibleMessages.length, 1);
  const slotHeight = Math.min(28, MSG_HEIGHT_BUDGET / Math.max(slotCount, 8));

  return (
    <>
      {/* Phase indicator */}
      <text x={20} y={20} fontFamily={FONT_FAMILY} fontSize={11} fontWeight={700} fill={INK} opacity={0.7}>
        TLS 1.3 — Phase {phase}{phase === 1 ? ": initial 1-RTT" : phase === 2 ? ": 0-RTT resume" : phase === 3 ? ": REPLAY ATTACK" : ": mitigations"}
      </text>

      {/* Actor columns */}
      <rect x={CLIENT_X} y={ACTOR_Y} width={COL_W} height={ACTOR_LABEL_H} fill={PAPER} stroke={INK} strokeWidth={1} />
      <text x={CLIENT_X + COL_W / 2} y={ACTOR_Y + 19} textAnchor="middle" fontFamily={FONT_FAMILY} fontSize={11} fontWeight={700} fill={INK}>CLIENT</text>
      <line x1={CLIENT_X + COL_W / 2} y1={ACTOR_Y + ACTOR_LABEL_H} x2={CLIENT_X + COL_W / 2} y2={MSG_BOTTOM} stroke={INK} strokeWidth={1} opacity={0.4} strokeDasharray="2 2" />

      <rect x={SERVER_X} y={ACTOR_Y} width={COL_W} height={ACTOR_LABEL_H} fill={PAPER} stroke={INK} strokeWidth={1} />
      <text x={SERVER_X + COL_W / 2} y={ACTOR_Y + 19} textAnchor="middle" fontFamily={FONT_FAMILY} fontSize={11} fontWeight={700} fill={INK}>SERVER</text>
      <line x1={SERVER_X + COL_W / 2} y1={ACTOR_Y + ACTOR_LABEL_H} x2={SERVER_X + COL_W / 2} y2={MSG_BOTTOM} stroke={INK} strokeWidth={1} opacity={0.4} strokeDasharray="2 2" />

      {showAttacker && (
        <>
          <rect x={ATTACKER_X} y={ACTOR_Y} width={COL_W} height={ACTOR_LABEL_H} fill={ACCENT} stroke={INK} strokeWidth={1} />
          <text x={ATTACKER_X + COL_W / 2} y={ACTOR_Y + 19} textAnchor="middle" fontFamily={FONT_FAMILY} fontSize={11} fontWeight={700} fill={INK}>ATTACKER</text>
          <line x1={ATTACKER_X + COL_W / 2} y1={ACTOR_Y + ACTOR_LABEL_H} x2={ATTACKER_X + COL_W / 2} y2={MSG_BOTTOM} stroke={INK} strokeWidth={1} opacity={0.4} strokeDasharray="2 2" />
        </>
      )}

      {/* Messages */}
      {visibleMessages.map((m, i) => {
        const y = MSG_TOP + i * slotHeight + slotHeight / 2;
        const fromX = actorX(m.from);
        const toX = actorX(m.to);
        const dir = toX > fromX ? 1 : -1;
        const isHighlighted = m.id === highlightedMessageId;
        const strokeC = m.replay ? ACCENT : INK;
        const strokeW = m.replay || isHighlighted ? 2 : 1;
        const midX = (fromX + toX) / 2;

        return (
          <g key={`msg-${m.id}`} opacity={isHighlighted ? 1 : 0.85}>
            {/* Line */}
            <line
              x1={fromX}
              y1={y}
              x2={toX}
              y2={y}
              stroke={strokeC}
              strokeWidth={strokeW}
              strokeDasharray={m.encrypted ? undefined : "4 3"}
            />
            {/* Arrowhead */}
            <polygon
              points={`${toX},${y} ${toX - dir * 6},${y - 3} ${toX - dir * 6},${y + 3}`}
              fill={strokeC}
            />
            {/* Label box */}
            <rect
              x={midX - 70}
              y={y - 10}
              width={140}
              height={20}
              fill={isHighlighted ? ACCENT : "#fff"}
              stroke={INK}
              strokeWidth={1}
              opacity={isHighlighted ? 1 : 0.95}
            />
            <text
              x={midX}
              y={y + 4}
              textAnchor="middle"
              fontFamily={FONT_FAMILY}
              fontSize={8}
              fill={INK}
              fontWeight={isHighlighted ? 700 : 500}
            >
              {m.label.length > 28 ? m.label.slice(0, 28) + "…" : m.label}
            </text>
          </g>
        );
      })}

      {/* Message at bottom — two-line wrap */}
      <rect x={10} y={MSG_Y_FOOTER - 28} width={460} height={36} fill={PAPER} stroke={INK} strokeWidth={1} />
      {(() => {
        const maxCharsPerLine = 78;
        const words = message.split(' ');
        let line1 = '';
        let line2 = '';
        for (const w of words) {
          if (!line1.length || (line1.length + w.length + 1) <= maxCharsPerLine) {
            line1 = line1 ? `${line1} ${w}` : w;
          } else if (!line2.length || (line2.length + w.length + 1) <= maxCharsPerLine) {
            line2 = line2 ? `${line2} ${w}` : w;
          } else {
            line2 = line2 + '…';
            break;
          }
        }
        return (
          <text x={16} y={MSG_Y_FOOTER - 12} fontFamily={FONT_FAMILY} fontSize={9} fontWeight={700} fill={INK}>
            <tspan x={16} dy={0}>{line1}</tspan>
            {line2 && <tspan x={16} dy={12}>{line2}</tspan>}
          </text>
        );
      })()}
    </>
  );
}

// Silence unused import warnings — SVG_W/SVG_H are layout constants
void SVG_W;
void SVG_H;

export function Tls0RttReplay(): ReactNode {
  return (
    <AlgoStepperShell<TLSState>
      title="RC-6 · TLS 1.3 0-RTT replay attack ⭐"
      desc="Initial 1-RTT handshake → session ticket → 0-RTT resumption → attacker replays captured EarlyData → server double-processes."
      frames={FRAMES}
      renderFrame={renderFrame}
      testIdPrefix="tls-0rtt-replay"
    />
  );
}
