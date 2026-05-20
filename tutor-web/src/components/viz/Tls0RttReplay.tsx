import type { ReactNode } from "react";
import { AlgoStepperShell, type Frame } from "./AlgoStepperShell";
import { ACCENT, FONT_FAMILY, INK, PAPER } from "./theme";
import {
  AnimatePresence,
  DrawLine,
  PopIn,
  motion,
} from "./motion-helpers";

type Actor = "client" | "server" | "attacker";
type Message = {
  id: number;
  from: Actor;
  to: Actor;
  label: string;
  encrypted: boolean;
  // replay=true means this message is a replayed (dangerous) transmission.
  // V12: danger is conveyed by ⚠ glyph + stroke-dash, NOT by yellow fill/stroke.
  replay: boolean;
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
  { id: 4, from: "server", to: "client", label: "NewSessionTicket (PSK)", encrypted: true, replay: false },
  { id: 5, from: "client", to: "server", label: "ClientHello + EarlyData('GET /transfer?amt=100')", encrypted: true, replay: false },
  { id: 6, from: "server", to: "client", label: "Accept 0-RTT. Process GET /transfer (charge $100).", encrypted: true, replay: false },
  { id: 7, from: "server", to: "client", label: "ServerHello + Finished + Response", encrypted: true, replay: false },
  { id: 8, from: "client", to: "attacker", label: "Attacker on-path: captured msg #5 bytes", encrypted: false, replay: false },
  { id: 9, from: "attacker", to: "server", label: "Re-send msg #5 bytes (REPLAY)", encrypted: true, replay: true },
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

type MitigationBadge = { id: string; text: string; appearOn: number };
const MITIGATION_BADGES: MitigationBadge[] = [
  { id: "mit-idempotent", text: "idempotent-only", appearOn: 10 },
  { id: "mit-singleuse", text: "single-use ticket", appearOn: 11 },
  { id: "mit-antireplay", text: "anti-replay window", appearOn: 12 },
];

function MessageRow({
  msg,
  y,
  isHighlighted,
}: {
  msg: Message;
  y: number;
  isHighlighted: boolean;
}) {
  const fromX = actorX(msg.from);
  const toX = actorX(msg.to);
  // V12: arrow color is always INK — replay danger is NOT encoded with yellow.
  // Replay messages instead use a thicker stroke-dash pattern and a ⚠ glyph.
  const strokeW = isHighlighted ? 2 : 1;
  const midX = (fromX + toX) / 2;
  // replay=true: use a 6 3 dash to distinguish from encrypted (4 3) and plain
  // (no dash) — encodes danger/replay KIND through line pattern, not color.
  const replayDash = "6 3";
  const encryptedDash = "4 3";
  const strokeDash = msg.replay ? replayDash : msg.encrypted ? encryptedDash : undefined;
  // Shrink label width so it never overhangs into the arrowhead zone at the
  // receiving column (was 140, hit the arrow tip on short hops like
  // server↔attacker which sit 120px apart).
  const span = Math.abs(toX - fromX);
  const labelW = Math.min(140, Math.max(60, span - 30));
  const maxChars = Math.max(12, Math.floor(labelW / 4.5));
  const labelText =
    msg.label.length > maxChars ? msg.label.slice(0, maxChars - 1) + "…" : msg.label;
  // V12: ACCENT label box is ONLY for the single highlighted (focal) message.
  const labelBoxFill = isHighlighted ? ACCENT : "#fff";
  // Place the label ABOVE the line so it never intersects with the arrowhead.
  const labelTop = y - 22;
  const labelH = 14;
  const labelTextY = y - 12;

  const ENTER = { duration: 0.3, delay: 0.1, ease: "easeOut" as const };

  return (
    <>
      {/* Line geometrically grows toward toX; SVG <marker> sits at the
          line endpoint and rotates with the line's direction. */}
      <DrawLine
        x1={fromX}
        y1={y}
        x2={toX}
        y2={y}
        stroke={INK}
        strokeWidth={strokeW}
        strokeDasharray={strokeDash}
        durationMs={400}
        markerEnd="url(#tls-arrow-ink)"
      />
      {/* Label box ABOVE the line (out of the arrow path).
          ACCENT fill = focal/highlighted message only (V12 single focal rule). */}
      <motion.rect
        x={midX - labelW / 2}
        y={labelTop}
        width={labelW}
        height={labelH}
        fill={labelBoxFill}
        stroke={INK}
        strokeWidth={1}
        initial={{ opacity: 0 }}
        animate={{ opacity: isHighlighted ? 1 : 0.95 }}
        transition={ENTER}
      />
      <motion.text
        x={midX}
        y={labelTextY}
        textAnchor="middle"
        fontFamily={FONT_FAMILY}
        fontSize={8}
        fill={INK}
        fontWeight={isHighlighted ? 700 : 500}
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={ENTER}
      >
        {labelText}
      </motion.text>
      {/* V12 replay danger: ⚠ glyph to the right of the label box encodes
          that this message is a replayed (potentially malicious) transmission.
          Uses INK — NOT yellow — so danger is communicated by shape/glyph, not color. */}
      {msg.replay && (
        <motion.text
          x={midX + labelW / 2 + 6}
          y={labelTextY}
          fontFamily={FONT_FAMILY}
          fontSize={9}
          fill={INK}
          fontWeight={700}
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={ENTER}
        >
          ⚠
        </motion.text>
      )}
    </>
  );
}

function FooterMessage({ message }: { message: string }) {
  const maxCharsPerLine = 78;
  const words = message.split(" ");
  let line1 = "";
  let line2 = "";
  let onLine2 = false;
  for (const w of words) {
    if (!onLine2) {
      if (!line1.length || (line1.length + w.length + 1) <= maxCharsPerLine) {
        line1 = line1 ? `${line1} ${w}` : w;
        continue;
      }
      onLine2 = true;
    }
    if (!line2.length || (line2.length + w.length + 1) <= maxCharsPerLine) {
      line2 = line2 ? `${line2} ${w}` : w;
    } else {
      line2 = line2 + "…";
      break;
    }
  }
  // tspan textContent concatenates without a separator — pad line1 so the
  // last word of line1 stays separated from the first word of line2.
  if (line2) {
    line1 = line1 + " ";
  }
  return (
    <AnimatePresence initial={false}>
      <motion.text
        key={message}
        x={16}
        y={MSG_Y_FOOTER - 12}
        fontFamily={FONT_FAMILY}
        fontSize={9}
        fontWeight={700}
        fill={INK}
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        exit={{ opacity: 0 }}
        transition={{ duration: 0.3 }}
      >
        <tspan x={16} dy={0}>
          {line1}
        </tspan>
        {line2 && (
          <tspan x={16} dy={12}>
            {line2}
          </tspan>
        )}
      </motion.text>
    </AnimatePresence>
  );
}

function renderFrame(frame: Frame<TLSState>): ReactNode {
  const { step, phase, visibleMessages, highlightedMessageId, message } = frame.state;
  const showAttacker = phase >= 3;
  const showMitigations = phase === 4;

  // Layout messages vertically by appearance order
  const slotCount = Math.max(visibleMessages.length, 1);
  const slotHeight = Math.min(28, MSG_HEIGHT_BUDGET / Math.max(slotCount, 8));

  return (
    <>
      {/* SVG markers for message arrowheads. orient="auto-start-reverse"
          rotates the marker to follow the line direction. refX positions
          the tip exactly at the line endpoint.
          V12: only the INK arrowhead is used — no ACCENT arrowhead variant. */}
      <defs>
        <marker
          id="tls-arrow-ink"
          viewBox="0 0 10 10"
          refX="9"
          refY="5"
          markerWidth="7"
          markerHeight="7"
          orient="auto-start-reverse"
          markerUnits="userSpaceOnUse"
        >
          <path d="M 0 0 L 10 5 L 0 10 z" fill={INK} />
        </marker>
      </defs>

      {/* Phase indicator — cross-fade as phase changes */}
      <AnimatePresence initial={false}>
        <motion.text
          key={`phase-${phase}`}
          x={20}
          y={20}
          fontFamily={FONT_FAMILY}
          fontSize={11}
          fontWeight={700}
          fill={INK}
          initial={{ opacity: 0 }}
          animate={{ opacity: 0.7 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.3 }}
        >
          TLS 1.3 — Phase {phase}
          {phase === 1
            ? ": initial 1-RTT"
            : phase === 2
            ? ": 0-RTT resume"
            : phase === 3
            ? ": REPLAY ATTACK"
            : ": mitigations"}
        </motion.text>
      </AnimatePresence>

      {/* CLIENT column (always visible) */}
      <rect
        x={CLIENT_X}
        y={ACTOR_Y}
        width={COL_W}
        height={ACTOR_LABEL_H}
        fill={PAPER}
        stroke={INK}
        strokeWidth={1}
      />
      <text
        x={CLIENT_X + COL_W / 2}
        y={ACTOR_Y + 19}
        textAnchor="middle"
        fontFamily={FONT_FAMILY}
        fontSize={11}
        fontWeight={700}
        fill={INK}
      >
        CLIENT
      </text>
      <line
        x1={CLIENT_X + COL_W / 2}
        y1={ACTOR_Y + ACTOR_LABEL_H}
        x2={CLIENT_X + COL_W / 2}
        y2={MSG_BOTTOM}
        stroke={INK}
        strokeWidth={1}
        opacity={0.4}
        strokeDasharray="2 2"
      />

      {/* SERVER column (always visible) */}
      <rect
        x={SERVER_X}
        y={ACTOR_Y}
        width={COL_W}
        height={ACTOR_LABEL_H}
        fill={PAPER}
        stroke={INK}
        strokeWidth={1}
      />
      <text
        x={SERVER_X + COL_W / 2}
        y={ACTOR_Y + 19}
        textAnchor="middle"
        fontFamily={FONT_FAMILY}
        fontSize={11}
        fontWeight={700}
        fill={INK}
      >
        SERVER
      </text>
      <line
        x1={SERVER_X + COL_W / 2}
        y1={ACTOR_Y + ACTOR_LABEL_H}
        x2={SERVER_X + COL_W / 2}
        y2={MSG_BOTTOM}
        stroke={INK}
        strokeWidth={1}
        opacity={0.4}
        strokeDasharray="2 2"
      />

      {/* ATTACKER column — appears in phase 3+.
          V12: actor columns must NOT use ACCENT fill — actor is a category, not a focus.
          ATTACKER header uses INK fill with PAPER text (high-contrast inversion)
          to signal threat actor presence without yellow/color encoding. */}
      <AnimatePresence>
        {showAttacker && (
          <PopIn key="attacker-col" durationMs={400}>
            <rect
              x={ATTACKER_X}
              y={ACTOR_Y}
              width={COL_W}
              height={ACTOR_LABEL_H}
              fill={INK}
              stroke={INK}
              strokeWidth={1}
            />
            <text
              x={ATTACKER_X + COL_W / 2}
              y={ACTOR_Y + 19}
              textAnchor="middle"
              fontFamily={FONT_FAMILY}
              fontSize={11}
              fontWeight={700}
              fill={PAPER}
            >
              ATTACKER
            </text>
            <line
              x1={ATTACKER_X + COL_W / 2}
              y1={ACTOR_Y + ACTOR_LABEL_H}
              x2={ATTACKER_X + COL_W / 2}
              y2={MSG_BOTTOM}
              stroke={INK}
              strokeWidth={1}
              opacity={0.4}
              strokeDasharray="2 2"
            />
          </PopIn>
        )}
      </AnimatePresence>

      {/* Messages — each draws on with DrawLine; key by msg.id so AnimatePresence
          tracks each as a stable identity. As more messages stack up, slotHeight
          shrinks so existing rows shift y; wrap MessageRow in motion.g animating
          y so rows SLIDE to their new positions instead of teleporting. */}
      <AnimatePresence>
        {visibleMessages.map((m, i) => {
          const targetY = MSG_TOP + i * slotHeight + slotHeight / 2;
          const isHighlighted = m.id === highlightedMessageId;
          return (
            <PopIn key={`msg-${m.id}`} durationMs={50}>
              <motion.g
                initial={false}
                animate={{ y: targetY }}
                transition={{ type: "tween", duration: 0.5, ease: "easeInOut" }}
              >
                <MessageRow msg={m} y={0} isHighlighted={isHighlighted} />
              </motion.g>
            </PopIn>
          );
        })}
      </AnimatePresence>

      {/* Mitigation badges — only in phase 4. Placed at the bottom of the
          lifeline area (y=296-308), below the last visible message and above
          the footer rect (y=312). Previously at y=44 they collided with the
          CLIENT/SERVER/ATTACKER actor column labels at y=40-70.
          V12: badge fill is PAPER (not ACCENT) — a badge is a category label, not a focus.
          Stroke-dash border distinguishes these from regular actor columns. */}
      <AnimatePresence>
        {showMitigations &&
          MITIGATION_BADGES.filter((b) => step >= b.appearOn).map((b, i) => (
            <PopIn key={b.id} durationMs={300} delayMs={i * 60}>
              <rect
                x={20 + i * 150}
                y={296}
                width={140}
                height={14}
                fill={PAPER}
                stroke={INK}
                strokeWidth={1}
                strokeDasharray="3 2"
              />
              <text
                x={20 + i * 150 + 70}
                y={306}
                textAnchor="middle"
                fontFamily={FONT_FAMILY}
                fontSize={8}
                fontWeight={700}
                fill={INK}
              >
                {b.text}
              </text>
            </PopIn>
          ))}
      </AnimatePresence>

      {/* Footer message — cross-fade on change */}
      <rect
        x={10}
        y={MSG_Y_FOOTER - 28}
        width={460}
        height={36}
        fill={PAPER}
        stroke={INK}
        strokeWidth={1}
      />
      <FooterMessage message={message} />
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
