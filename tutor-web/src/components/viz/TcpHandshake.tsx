import type { ReactNode } from "react";
import { AlgoStepperShell, type Frame } from "./AlgoStepperShell";
import { ACCENT, FONT_FAMILY, HATCH_DENSE, INK, PAPER } from "./theme";
import {
  AnimatePresence,
  DrawLine,
  FadeText,
  PopIn,
  motion,
} from "./motion-helpers";

type Phase = "NORMAL" | "FLOOD" | "COOKIES" | "SUMMARY";
type TCPState =
  | "CLOSED"
  | "LISTEN"
  | "SYN_SENT"
  | "SYN_RCVD"
  | "ESTABLISHED";
type Actor = "CLIENT" | "SERVER" | "ATTACKER";
type MsgKind = "SYN" | "SYN-ACK" | "ACK" | "DROP";

type Message = {
  id: string;
  from: Actor;
  to: Actor;
  kind: MsgKind;
  seq?: number;
  ack?: number;
  spoofedSrc?: string;
  // Visual position on the swimlane (y).
  y: number;
};

type BacklogSlot = { src: string; isn: number; spoofed: boolean } | null;

type State = {
  step: number;
  phase: Phase;
  clientState: TCPState;
  serverState: TCPState;
  inFlight: Message[];
  backlog: BacklogSlot[]; // length 4
  cookiesEnabled: boolean;
  attackerActive: boolean;
  drop?: { msgId: string };
  message: string;
};

// Fixed ISN values for the visualization — large random-looking integers so
// the point of "ISN randomization" comes across visually.
const ISN_CLIENT = 4093817;
const ISN_SERVER = 4901234;

// Layout constants (viewBox 0 0 480 360).
const CLIENT_X = 80;
const SERVER_X = 400;
const ATTACKER_X = 240;

const ACTOR_LABEL_Y = 18;
const ACTOR_LABEL_BAR_Y = 30;
const ACTOR_LABEL_BAR_H = 24;
const STATE_LABEL_Y = 70;

const LIFELINE_TOP = 56;
const LIFELINE_BOTTOM = 270;

const MSG_TOP = 90;
const MSG_ROW_H = 24;

const BACKLOG_X = 446;
const BACKLOG_Y = 90;
const BACKLOG_SLOT_W = 22;
const BACKLOG_SLOT_H = 22;
const BACKLOG_SLOT_GAP = 4;

const FOOTER_Y = 312;

const EMPTY_BACKLOG: BacklogSlot[] = [null, null, null, null];

function setBacklogSlot(
  backlog: BacklogSlot[],
  idx: number,
  slot: BacklogSlot
): BacklogSlot[] {
  const next = backlog.slice();
  next[idx] = slot;
  return next;
}

function buildFrames(): Frame<State>[] {
  const frames: Frame<State>[] = [];

  const mk = (s: State, aria: string): Frame<State> => ({
    state: {
      ...s,
      backlog: s.backlog.map((b) => (b ? { ...b } : null)),
      inFlight: s.inFlight.map((m) => ({ ...m })),
    },
    aria,
  });

  // ---- PHASE A — NORMAL HANDSHAKE ----
  // F0 — INIT
  frames.push(
    mk(
      {
        step: 0,
        phase: "NORMAL",
        clientState: "CLOSED",
        serverState: "LISTEN",
        inFlight: [],
        backlog: EMPTY_BACKLOG,
        cookiesEnabled: false,
        attackerActive: false,
        message:
          "INIT. Server LISTEN with backlog (capacity 4). Client CLOSED. About to dial.",
      },
      "INIT. Client CLOSED, server LISTEN."
    )
  );

  // F1 — client SYN
  frames.push(
    mk(
      {
        step: 1,
        phase: "NORMAL",
        clientState: "SYN_SENT",
        serverState: "LISTEN",
        inFlight: [
          {
            id: "n-syn",
            from: "CLIENT",
            to: "SERVER",
            kind: "SYN",
            seq: ISN_CLIENT,
            y: MSG_TOP,
          },
        ],
        backlog: EMPTY_BACKLOG,
        cookiesEnabled: false,
        attackerActive: false,
        message: `Client → Server: SYN seq=${ISN_CLIENT}. ISN is a random 32-bit integer, not 0.`,
      },
      "client sends SYN."
    )
  );

  // F2 — server receives SYN, allocates backlog slot.
  // Keep the SYN arrow visible so the user sees the message that just arrived;
  // the new backlog slot animates in alongside.
  frames.push(
    mk(
      {
        step: 2,
        phase: "NORMAL",
        clientState: "SYN_SENT",
        serverState: "SYN_RCVD",
        inFlight: [
          {
            id: "n-syn",
            from: "CLIENT",
            to: "SERVER",
            kind: "SYN",
            seq: ISN_CLIENT,
            y: MSG_TOP,
          },
        ],
        backlog: setBacklogSlot(EMPTY_BACKLOG, 0, {
          src: "client",
          isn: ISN_CLIENT,
          spoofed: false,
        }),
        cookiesEnabled: false,
        attackerActive: false,
        message:
          "Server receives SYN. Allocates backlog slot 1 = {src=client, ISN=" +
          ISN_CLIENT +
          "}. State = SYN_RCVD.",
      },
      "server allocates backlog slot."
    )
  );

  // F3 — server SYN-ACK
  frames.push(
    mk(
      {
        step: 3,
        phase: "NORMAL",
        clientState: "SYN_SENT",
        serverState: "SYN_RCVD",
        inFlight: [
          {
            id: "n-synack",
            from: "SERVER",
            to: "CLIENT",
            kind: "SYN-ACK",
            seq: ISN_SERVER,
            ack: ISN_CLIENT + 1,
            y: MSG_TOP + MSG_ROW_H,
          },
        ],
        backlog: setBacklogSlot(EMPTY_BACKLOG, 0, {
          src: "client",
          isn: ISN_CLIENT,
          spoofed: false,
        }),
        cookiesEnabled: false,
        attackerActive: false,
        message: `Server → Client: SYN-ACK seq=${ISN_SERVER} ack=${ISN_CLIENT + 1}. Server picks its own random ISN.`,
      },
      "server sends SYN-ACK."
    )
  );

  // F4 — client receives SYN-ACK → ESTABLISHED on client side.
  // Keep the SYN-ACK arrow visible to show what the client just validated.
  frames.push(
    mk(
      {
        step: 4,
        phase: "NORMAL",
        clientState: "ESTABLISHED",
        serverState: "SYN_RCVD",
        inFlight: [
          {
            id: "n-synack",
            from: "SERVER",
            to: "CLIENT",
            kind: "SYN-ACK",
            seq: ISN_SERVER,
            ack: ISN_CLIENT + 1,
            y: MSG_TOP + MSG_ROW_H,
          },
        ],
        backlog: setBacklogSlot(EMPTY_BACKLOG, 0, {
          src: "client",
          isn: ISN_CLIENT,
          spoofed: false,
        }),
        cookiesEnabled: false,
        attackerActive: false,
        message:
          "Client validates ack=" +
          (ISN_CLIENT + 1) +
          ". State = ESTABLISHED on client side.",
      },
      "client moves to ESTABLISHED."
    )
  );

  // F5 — client ACK
  frames.push(
    mk(
      {
        step: 5,
        phase: "NORMAL",
        clientState: "ESTABLISHED",
        serverState: "SYN_RCVD",
        inFlight: [
          {
            id: "n-ack",
            from: "CLIENT",
            to: "SERVER",
            kind: "ACK",
            seq: ISN_CLIENT + 1,
            ack: ISN_SERVER + 1,
            y: MSG_TOP + 2 * MSG_ROW_H,
          },
        ],
        backlog: setBacklogSlot(EMPTY_BACKLOG, 0, {
          src: "client",
          isn: ISN_CLIENT,
          spoofed: false,
        }),
        cookiesEnabled: false,
        attackerActive: false,
        message: `Client → Server: ACK seq=${ISN_CLIENT + 1} ack=${ISN_SERVER + 1}.`,
      },
      "client sends ACK."
    )
  );

  // F6 — server moves slot → accept queue, ESTABLISHED on both sides.
  // Keep the ACK arrow visible so the viewer can see the message that triggered
  // the transition; backlog drains (slot moves to the accept queue, off-stage).
  frames.push(
    mk(
      {
        step: 6,
        phase: "NORMAL",
        clientState: "ESTABLISHED",
        serverState: "ESTABLISHED",
        inFlight: [
          {
            id: "n-ack",
            from: "CLIENT",
            to: "SERVER",
            kind: "ACK",
            seq: ISN_CLIENT + 1,
            ack: ISN_SERVER + 1,
            y: MSG_TOP + 2 * MSG_ROW_H,
          },
        ],
        backlog: EMPTY_BACKLOG,
        cookiesEnabled: false,
        attackerActive: false,
        message:
          "Server moves slot 1 to accept queue. Both ESTABLISHED. ISN randomization stops off-path attackers from guessing seq to inject data.",
      },
      "both ESTABLISHED."
    )
  );

  // ---- PHASE B — SYN FLOOD ----
  // F7 — attacker appears + spoofed SYN #1
  frames.push(
    mk(
      {
        step: 7,
        phase: "FLOOD",
        clientState: "CLOSED",
        serverState: "LISTEN",
        inFlight: [
          {
            id: "f-syn-1",
            from: "ATTACKER",
            to: "SERVER",
            kind: "SYN",
            spoofedSrc: "fake_1",
            seq: 1111111,
            y: MSG_TOP,
          },
        ],
        backlog: EMPTY_BACKLOG,
        cookiesEnabled: false,
        attackerActive: true,
        message:
          "ATTACKER appears. Sends SYN with spoofed src=fake_1. Server can't tell it's fake.",
      },
      "SYN flood begins."
    )
  );

  // F8 — slot 1 fills with fake_1
  frames.push(
    mk(
      {
        step: 8,
        phase: "FLOOD",
        clientState: "CLOSED",
        serverState: "SYN_RCVD",
        inFlight: [],
        backlog: setBacklogSlot(EMPTY_BACKLOG, 0, {
          src: "fake_1",
          isn: 1111111,
          spoofed: true,
        }),
        cookiesEnabled: false,
        attackerActive: true,
        message:
          "Server allocates slot 1 = {src=fake_1, ...}. Sends SYN-ACK into the void — fake_1 doesn't exist so no ACK comes back.",
      },
      "slot 1 filled by spoofed SYN."
    )
  );

  // F9 — slot 2 fills
  {
    const b9 = setBacklogSlot(
      setBacklogSlot(EMPTY_BACKLOG, 0, {
        src: "fake_1",
        isn: 1111111,
        spoofed: true,
      }),
      1,
      { src: "fake_2", isn: 2222222, spoofed: true }
    );
    frames.push(
      mk(
        {
          step: 9,
          phase: "FLOOD",
          clientState: "CLOSED",
          serverState: "SYN_RCVD",
          inFlight: [],
          backlog: b9,
          cookiesEnabled: false,
          attackerActive: true,
          message:
            "Attacker SYN spoofed src=fake_2 → slot 2 filled. Backlog growing.",
        },
        "slot 2 filled."
      )
    );
  }

  // F10 — slot 3 fills
  {
    const b10: BacklogSlot[] = [
      { src: "fake_1", isn: 1111111, spoofed: true },
      { src: "fake_2", isn: 2222222, spoofed: true },
      { src: "fake_3", isn: 3333333, spoofed: true },
      null,
    ];
    frames.push(
      mk(
        {
          step: 10,
          phase: "FLOOD",
          clientState: "CLOSED",
          serverState: "SYN_RCVD",
          inFlight: [],
          backlog: b10,
          cookiesEnabled: false,
          attackerActive: true,
          message:
            "Attacker SYN spoofed src=fake_3 → slot 3 filled. One slot remaining.",
        },
        "slot 3 filled."
      )
    );
  }

  // F11 — slot 4 fills → BACKLOG FULL
  {
    const b11: BacklogSlot[] = [
      { src: "fake_1", isn: 1111111, spoofed: true },
      { src: "fake_2", isn: 2222222, spoofed: true },
      { src: "fake_3", isn: 3333333, spoofed: true },
      { src: "fake_4", isn: 4444444, spoofed: true },
    ];
    frames.push(
      mk(
        {
          step: 11,
          phase: "FLOOD",
          clientState: "CLOSED",
          serverState: "SYN_RCVD",
          inFlight: [],
          backlog: b11,
          cookiesEnabled: false,
          attackerActive: true,
          message:
            "⚠ BACKLOG FULL. Server cannot accept any new SYNs until these half-open connections time out.",
        },
        "backlog full."
      )
    );
  }

  // F12 — legitimate client SYN dropped
  {
    const b12: BacklogSlot[] = [
      { src: "fake_1", isn: 1111111, spoofed: true },
      { src: "fake_2", isn: 2222222, spoofed: true },
      { src: "fake_3", isn: 3333333, spoofed: true },
      { src: "fake_4", isn: 4444444, spoofed: true },
    ];
    frames.push(
      mk(
        {
          step: 12,
          phase: "FLOOD",
          clientState: "SYN_SENT",
          serverState: "SYN_RCVD",
          inFlight: [
            {
              id: "f-syn-legit",
              from: "CLIENT",
              to: "SERVER",
              kind: "SYN",
              seq: ISN_CLIENT,
              y: MSG_TOP,
            },
          ],
          backlog: b12,
          cookiesEnabled: false,
          attackerActive: true,
          drop: { msgId: "f-syn-legit" },
          message:
            "Legitimate client SYN arrives → backlog full → SERVER DROPS. Connection refused. Denial of service.",
        },
        "legitimate SYN dropped."
      )
    );
  }

  // ---- PHASE C — SYN COOKIES DEFENSE ----
  // F13 — cookies on, backlog reset
  frames.push(
    mk(
      {
        step: 13,
        phase: "COOKIES",
        clientState: "CLOSED",
        serverState: "LISTEN",
        inFlight: [],
        backlog: EMPTY_BACKLOG,
        cookiesEnabled: true,
        attackerActive: true,
        message:
          "SYN COOKIES ON. Server stops allocating backlog state. Cookie = hash(src_ip, dst_ip, t, secret) embedded in SYN-ACK seq.",
      },
      "SYN cookies enabled."
    )
  );

  // F14 — attacker spoofed SYN under cookies → server replies but no backlog
  frames.push(
    mk(
      {
        step: 14,
        phase: "COOKIES",
        clientState: "CLOSED",
        serverState: "LISTEN",
        inFlight: [
          {
            id: "c-syn-attacker",
            from: "ATTACKER",
            to: "SERVER",
            kind: "SYN",
            spoofedSrc: "fake_x",
            seq: 5555555,
            y: MSG_TOP,
          },
          {
            id: "c-synack-attacker",
            from: "SERVER",
            to: "CLIENT", // visually goes "back" but spoofed src means it goes to fake address
            kind: "SYN-ACK",
            seq: 9876543, // cookie value
            ack: 5555556,
            spoofedSrc: "fake_x",
            y: MSG_TOP + MSG_ROW_H,
          },
        ],
        backlog: EMPTY_BACKLOG,
        cookiesEnabled: true,
        attackerActive: true,
        message:
          "Attacker SYN → server SYN-ACK with seq=cookie. NO backlog allocated. Reply goes to fake_x — attacker never hears it, no ACK arrives.",
      },
      "cookie reply to spoofed src, no state."
    )
  );

  // F15 — legitimate client completes handshake via cookie verify
  frames.push(
    mk(
      {
        step: 15,
        phase: "COOKIES",
        clientState: "ESTABLISHED",
        serverState: "ESTABLISHED",
        inFlight: [
          {
            id: "c-syn-legit",
            from: "CLIENT",
            to: "SERVER",
            kind: "SYN",
            seq: ISN_CLIENT,
            y: MSG_TOP,
          },
          {
            id: "c-synack-legit",
            from: "SERVER",
            to: "CLIENT",
            kind: "SYN-ACK",
            seq: 7777777, // cookie value
            ack: ISN_CLIENT + 1,
            y: MSG_TOP + MSG_ROW_H,
          },
          {
            id: "c-ack-legit",
            from: "CLIENT",
            to: "SERVER",
            kind: "ACK",
            seq: ISN_CLIENT + 1,
            ack: 7777778,
            y: MSG_TOP + 2 * MSG_ROW_H,
          },
        ],
        backlog: EMPTY_BACKLOG,
        cookiesEnabled: true,
        attackerActive: true,
        message:
          "Legitimate client: ACK arrives. Server re-hashes → matches cookie → ESTABLISHED. Backlog never grew under attack.",
      },
      "cookie verified, both ESTABLISHED."
    )
  );

  // ---- PHASE D — SUMMARY ----
  frames.push(
    mk(
      {
        step: 16,
        phase: "SUMMARY",
        clientState: "ESTABLISHED",
        serverState: "ESTABLISHED",
        inFlight: [],
        backlog: EMPTY_BACKLOG,
        cookiesEnabled: true,
        attackerActive: true,
        message:
          "Lesson: ISN randomization defeats seq-prediction injection. SYN cookies defeat backlog exhaustion. Defenses layer.",
      },
      "summary."
    )
  );

  return frames;
}

export const FRAMES = buildFrames();
export const FRAME_COUNT = FRAMES.length;

function actorX(a: Actor): number {
  if (a === "CLIENT") return CLIENT_X;
  if (a === "SERVER") return SERVER_X;
  return ATTACKER_X;
}

function formatStateLabel(s: TCPState): string {
  return s;
}

/**
 * Encode message KIND via non-color channels (V12 compliance):
 *   SYN     → solid line  (no dash)
 *   SYN-ACK → long-dash   ("5 3")  — distinguishable as a reply
 *   ACK     → short-dash  ("2 3")  — the final completing stroke
 *   DROP    → dotted      ("3 2")  — message that never reaches destination
 *
 * Spoofed messages additionally use a thinner stroke weight.
 * All arrows are INK (not ACCENT). ACCENT is not used here.
 */
function kindDash(kind: MsgKind, isDropped: boolean): string | undefined {
  if (isDropped) return "3 2";
  if (kind === "SYN-ACK") return "5 3";
  if (kind === "ACK") return "2 3";
  return undefined; // SYN = solid
}

function MessageArrow({
  msg,
  isDropped,
}: {
  msg: Message;
  isDropped: boolean;
}) {
  const fromX = actorX(msg.from);
  const toX = actorX(msg.to);
  const midX = (fromX + toX) / 2;
  const y = msg.y;

  // V12: all arrows are INK — kind is encoded by dash pattern, not color.
  const strokeC = INK;
  const dash = kindDash(msg.kind, isDropped);
  // Spoofed messages use a slightly thinner stroke to signal "not a real source".
  const strokeW = isDropped ? 2 : msg.spoofedSrc ? 1 : 1.5;

  // Label text — kind + seq/ack numbers + spoofed-src annotation.
  const seqPart =
    msg.seq !== undefined ? ` seq=${msg.seq}` : "";
  const ackPart = msg.ack !== undefined ? ` ack=${msg.ack}` : "";
  const srcPart = msg.spoofedSrc ? ` src=${msg.spoofedSrc}` : "";
  const labelText = `${msg.kind}${seqPart}${ackPart}${srcPart}`;

  // Label sizing
  const span = Math.abs(toX - fromX);
  const labelW = Math.min(180, Math.max(70, span - 50));
  const maxChars = Math.max(14, Math.floor(labelW / 4.6));
  const labelDisplay =
    labelText.length > maxChars
      ? labelText.slice(0, maxChars - 1) + "…"
      : labelText;

  // Place label above the line
  const labelH = 12;
  const labelTop = y - labelH - 2;
  const labelTextY = y - 6;

  // For dropped messages, shorten the arrow to mid-way so the × sits at the
  // endpoint as the message gets refused.
  const effectiveToX = isDropped ? (fromX + toX) / 2 + 30 : toX;

  return (
    <>
      <DrawLine
        x1={fromX}
        y1={y}
        x2={effectiveToX}
        y2={y}
        stroke={strokeC}
        strokeWidth={strokeW}
        strokeDasharray={dash}
        durationMs={500}
        markerEnd={isDropped ? undefined : "url(#tcp-arrow-ink)"}
      />
      <motion.rect
        x={midX - labelW / 2}
        y={labelTop}
        width={labelW}
        height={labelH}
        fill="#fff"
        stroke={INK}
        strokeWidth={0.75}
        initial={{ opacity: 0 }}
        animate={{ opacity: 0.95 }}
        transition={{ duration: 0.3, delay: 0.1, ease: "easeOut" }}
      />
      <motion.text
        x={midX}
        y={labelTextY}
        textAnchor="middle"
        fontFamily={FONT_FAMILY}
        fontSize={8}
        fontWeight={700}
        fill={INK}
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ duration: 0.3, delay: 0.1, ease: "easeOut" }}
      >
        {labelDisplay}
      </motion.text>
      {isDropped && (
        <PopIn durationMs={300} delayMs={250}>
          {/* V12: drop × is INK, not ACCENT — "dropped" is a state, not a
              focal element. A thick INK × with PAPER outline is legible. */}
          <text
            x={effectiveToX + 6}
            y={y + 4}
            textAnchor="middle"
            fontFamily={FONT_FAMILY}
            fontSize={14}
            fontWeight={700}
            fill={INK}
            stroke={PAPER}
            strokeWidth={1.5}
          >
            ×
          </text>
        </PopIn>
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
      if (!line1.length || line1.length + w.length + 1 <= maxCharsPerLine) {
        line1 = line1 ? `${line1} ${w}` : w;
        continue;
      }
      onLine2 = true;
    }
    if (!line2.length || line2.length + w.length + 1 <= maxCharsPerLine) {
      line2 = line2 ? `${line2} ${w}` : w;
    } else {
      line2 = line2 + "…";
      break;
    }
  }
  // SVG <tspan>s concatenate without a separator when serialized as
  // textContent (and when read by screen readers), so the last word of line1
  // would butt against the first word of line2 ("randomISN" instead of
  // "random ISN"). Pad line1 with a trailing space whenever there's a line2.
  if (line2) {
    line1 = line1 + " ";
  }
  return (
    <AnimatePresence initial={false} mode="wait">
      <motion.text
        key={message}
        x={16}
        y={FOOTER_Y + 14}
        fontFamily={FONT_FAMILY}
        fontSize={9}
        fontWeight={700}
        fill={INK}
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        exit={{ opacity: 0 }}
        transition={{ duration: 0.18 }}
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

function renderFrame(frame: Frame<State>): ReactNode {
  const {
    phase,
    clientState,
    serverState,
    inFlight,
    backlog,
    cookiesEnabled,
    attackerActive,
    drop,
    message,
  } = frame.state;

  const backlogFull = backlog.every((s) => s !== null);

  return (
    <>
      {/* SVG markers for message arrowheads — V12: only INK arrowhead.
          The old tcp-arrow-accent marker is removed (yellow arrowheads
          encoded kind, which violates V12). */}
      <defs>
        <marker
          id="tcp-arrow-ink"
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

      {/* Phase indicator (top) — single static title that updates on phase
          change. AnimatePresence with mode="wait" guarantees the previous title
          fully exits before the next one enters, so two titles never overlap at
          mid-opacity (previous bug: residual stacked titles). */}
      <AnimatePresence initial={false} mode="wait">
        <motion.text
          key={`phase-${phase}`}
          x={16}
          y={ACTOR_LABEL_Y}
          fontFamily={FONT_FAMILY}
          fontSize={11}
          fontWeight={700}
          fill={INK}
          initial={{ opacity: 0 }}
          animate={{ opacity: 0.75 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.18 }}
        >
          TCP HANDSHAKE —{" "}
          {phase === "NORMAL"
            ? "normal 3-way"
            : phase === "FLOOD"
            ? "SYN FLOOD ATTACK"
            : phase === "COOKIES"
            ? "SYN COOKIES DEFENSE"
            : "SUMMARY"}
        </motion.text>
      </AnimatePresence>

      {/* CLIENT actor column. V12: SUMMARY "victory" state is encoded by a
          double-weight INK border + label text, not by ACCENT fill (which would
          be a category color and may collide with the focal-element rule). */}
      <rect
        x={CLIENT_X - 32}
        y={ACTOR_LABEL_BAR_Y}
        width={64}
        height={ACTOR_LABEL_BAR_H}
        fill={PAPER}
        stroke={INK}
        strokeWidth={phase === "SUMMARY" ? 2.5 : 1}
      />
      <text
        x={CLIENT_X}
        y={ACTOR_LABEL_BAR_Y + 16}
        textAnchor="middle"
        fontFamily={FONT_FAMILY}
        fontSize={11}
        fontWeight={700}
        fill={INK}
      >
        CLIENT
      </text>
      <line
        x1={CLIENT_X}
        y1={LIFELINE_TOP}
        x2={CLIENT_X}
        y2={LIFELINE_BOTTOM}
        stroke={INK}
        strokeWidth={1}
        opacity={0.4}
        strokeDasharray="2 2"
      />
      <FadeText
        x={CLIENT_X}
        y={STATE_LABEL_Y}
        textAnchor="middle"
        fontFamily={FONT_FAMILY}
        fontSize={9}
        fontWeight={700}
        fill={INK}
        opacity={0.85}
      >
        {formatStateLabel(clientState)}
      </FadeText>

      {/* SERVER actor column. V12: same double-weight INK border as CLIENT
          on the SUMMARY frame — no ACCENT fill. */}
      <rect
        x={SERVER_X - 32}
        y={ACTOR_LABEL_BAR_Y}
        width={64}
        height={ACTOR_LABEL_BAR_H}
        fill={PAPER}
        stroke={INK}
        strokeWidth={phase === "SUMMARY" ? 2.5 : 1}
      />
      <text
        x={SERVER_X}
        y={ACTOR_LABEL_BAR_Y + 16}
        textAnchor="middle"
        fontFamily={FONT_FAMILY}
        fontSize={11}
        fontWeight={700}
        fill={INK}
      >
        SERVER
      </text>
      <line
        x1={SERVER_X}
        y1={LIFELINE_TOP}
        x2={SERVER_X}
        y2={LIFELINE_BOTTOM}
        stroke={INK}
        strokeWidth={1}
        opacity={0.4}
        strokeDasharray="2 2"
      />
      <FadeText
        x={SERVER_X}
        y={STATE_LABEL_Y}
        textAnchor="middle"
        fontFamily={FONT_FAMILY}
        fontSize={9}
        fontWeight={700}
        fill={INK}
        opacity={0.85}
      >
        {formatStateLabel(serverState)}
      </FadeText>

      {/* ATTACKER actor column — only visible when active. V12: the ATTACKER
          header must NOT use ACCENT fill (yellow = focal element, not actor
          category). Instead, a dashed INK border distinguishes the ATTACKER
          column as a threat actor without using yellow. On the SUMMARY frame
          the column gets a lighter opacity + strikethrough to signal defeat. */}
      <AnimatePresence>
        {attackerActive && (
          <PopIn key="attacker-col" durationMs={350}>
            <rect
              x={ATTACKER_X - 36}
              y={ACTOR_LABEL_BAR_Y}
              width={72}
              height={ACTOR_LABEL_BAR_H}
              fill={PAPER}
              stroke={INK}
              strokeWidth={phase === "SUMMARY" ? 1 : 1.5}
              strokeDasharray={phase === "SUMMARY" ? undefined : "3 2"}
            />
            <text
              x={ATTACKER_X}
              y={ACTOR_LABEL_BAR_Y + 16}
              textAnchor="middle"
              fontFamily={FONT_FAMILY}
              fontSize={11}
              fontWeight={700}
              fill={INK}
              opacity={phase === "SUMMARY" ? 0.45 : 1}
              textDecoration={phase === "SUMMARY" ? "line-through" : undefined}
            >
              ATTACKER
            </text>
            {phase === "SUMMARY" && (
              <text
                x={ATTACKER_X}
                y={ACTOR_LABEL_BAR_Y + ACTOR_LABEL_BAR_H + 12}
                textAnchor="middle"
                fontFamily={FONT_FAMILY}
                fontSize={8}
                fontWeight={700}
                fill={INK}
              >
                DEFEATED
              </text>
            )}
            <line
              x1={ATTACKER_X}
              y1={LIFELINE_TOP}
              x2={ATTACKER_X}
              y2={LIFELINE_BOTTOM}
              stroke={INK}
              strokeWidth={1}
              opacity={phase === "SUMMARY" ? 0.15 : 0.4}
              strokeDasharray="2 2"
            />
          </PopIn>
        )}
      </AnimatePresence>

      {/* Backlog queue (right edge) — 4 stacked slots */}
      <text
        x={BACKLOG_X + BACKLOG_SLOT_W / 2}
        y={BACKLOG_Y - 12}
        textAnchor="middle"
        fontFamily={FONT_FAMILY}
        fontSize={8}
        fontWeight={700}
        fill={INK}
        opacity={0.7}
      >
        BACKLOG
      </text>
      {backlog.map((slot, i) => {
        const slotY = BACKLOG_Y + i * (BACKLOG_SLOT_H + BACKLOG_SLOT_GAP);
        return (
          <g key={`backlog-frame-${i}`}>
            <rect
              x={BACKLOG_X}
              y={slotY}
              width={BACKLOG_SLOT_W}
              height={BACKLOG_SLOT_H}
              fill={PAPER}
              stroke={INK}
              strokeWidth={1}
            />
            <text
              x={BACKLOG_X + BACKLOG_SLOT_W / 2}
              y={slotY + BACKLOG_SLOT_H + 9}
              textAnchor="middle"
              fontFamily={FONT_FAMILY}
              fontSize={7}
              fill={INK}
              opacity={0.55}
            >
              {i + 1}
            </text>
            <AnimatePresence>
              {slot && (
                <PopIn key={`slot-${i}-${slot.src}`} durationMs={300}>
                  {/* V12: spoofed slots use HATCH_DENSE fill (category =
                      "half-open / spoofed"), not ACCENT (which is focal only).
                      Legitimate slots use solid INK fill. */}
                  <rect
                    x={BACKLOG_X}
                    y={slotY}
                    width={BACKLOG_SLOT_W}
                    height={BACKLOG_SLOT_H}
                    fill={slot.spoofed ? HATCH_DENSE : INK}
                    stroke={INK}
                    strokeWidth={1}
                  />
                  <text
                    x={BACKLOG_X + BACKLOG_SLOT_W / 2}
                    y={slotY + 14}
                    textAnchor="middle"
                    fontFamily={FONT_FAMILY}
                    fontSize={6.5}
                    fontWeight={700}
                    fill={INK}
                  >
                    {slot.src}
                  </text>
                </PopIn>
              )}
            </AnimatePresence>
          </g>
        );
      })}
      {backlogFull && (
        <PopIn durationMs={300}>
          {/* V12: BACKLOG FULL alert uses a thick INK border (not ACCENT stroke).
              "Full" is a state category — not the focal element of the frame. */}
          <rect
            x={BACKLOG_X - 4}
            y={BACKLOG_Y - 4}
            width={BACKLOG_SLOT_W + 8}
            height={
              4 * BACKLOG_SLOT_H + 3 * BACKLOG_SLOT_GAP + 8
            }
            fill="none"
            stroke={INK}
            strokeWidth={2.5}
          />
          <text
            x={BACKLOG_X + BACKLOG_SLOT_W / 2}
            y={
              BACKLOG_Y +
              4 * BACKLOG_SLOT_H +
              3 * BACKLOG_SLOT_GAP +
              22
            }
            textAnchor="middle"
            fontFamily={FONT_FAMILY}
            fontSize={7}
            fontWeight={700}
            fill={INK}
          >
            ⚠ FULL
          </text>
        </PopIn>
      )}

      {/* Cookies-on indicator near the server lifeline. V12: mode badges are
          NOT ACCENT fill — "cookies enabled" is a mode, not a focal element.
          Use PAPER fill + double-weight INK border to distinguish from the
          normal server lifeline area. */}
      <AnimatePresence>
        {cookiesEnabled && (
          <PopIn key="cookies-on" durationMs={350}>
            <rect
              x={SERVER_X - 80}
              y={210}
              width={160}
              height={32}
              fill={PAPER}
              stroke={INK}
              strokeWidth={2}
            />
            <text
              x={SERVER_X}
              y={222}
              textAnchor="middle"
              fontFamily={FONT_FAMILY}
              fontSize={8}
              fontWeight={700}
              fill={INK}
            >
              SYN COOKIES ON
            </text>
            <text
              x={SERVER_X}
              y={234}
              textAnchor="middle"
              fontFamily={FONT_FAMILY}
              fontSize={7}
              fill={INK}
            >
              cookie = hash(src,dst,t,secret)
            </text>
          </PopIn>
        )}
      </AnimatePresence>

      {/* In-flight messages */}
      <AnimatePresence>
        {inFlight.map((m) => {
          const isDropped = drop?.msgId === m.id;
          return (
            <PopIn key={`msg-${m.id}`} durationMs={50}>
              <MessageArrow msg={m} isDropped={isDropped} />
            </PopIn>
          );
        })}
      </AnimatePresence>

      {/* Footer */}
      <rect
        x={10}
        y={FOOTER_Y}
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

export function TcpHandshake(): ReactNode {
  return (
    <AlgoStepperShell<State>
      title="RC-2 · TCP 3-way handshake + SYN flood + SYN cookies"
      desc="Normal 3-way handshake with ISN randomization → SYN flood fills server backlog → SYN cookies defend without backlog allocation."
      frames={FRAMES}
      renderFrame={renderFrame}
      testIdPrefix="tcp-handshake"
    />
  );
}
