import type { ReactNode } from "react";
import { scaleLinear } from "@visx/scale";
import { AlgoStepperShell, type Frame } from "./AlgoStepperShell";
import { ACCENT, FONT_FAMILY, INK, PAPER } from "./theme";
import {
  AnimatePresence,
  PopIn,
  motion,
} from "./motion-helpers";

type Layer = "L7" | "L6" | "L5" | "L4" | "L3" | "L2" | "L1";
type Side = "SENDER" | "RECEIVER";
type Packet = {
  id: string;
  side: Side;
  layer: Layer;
  payload: number; // bytes (excludes headers)
  headers: { tcp?: boolean; ip?: boolean; eth?: boolean; crc?: boolean };
  fragInfo?: { offset: number; mf: 0 | 1; total: number };
  label: string;
};
type EventKind =
  | "fragment"
  | "reassemble"
  | "header-add"
  | "header-strip"
  | "wire-transit"
  | "init";
type State = {
  step: number;
  packets: Packet[];
  highlightLayer?: { side: Side; layer: Layer };
  event?: EventKind;
  reassemblyBuffer?: { fragments: Packet[] };
  message: string;
};

const APP_PAYLOAD = 1400;
const TCP_HDR = 20;
const IP_HDR = 20;
const ETH_HDR = 14;
const ETH_CRC = 4;
const MTU = 800;

// Per-fragment payload arithmetic:
//   L4 datagram (after TCP): 1420
//   L3 datagram (after IP):  1440
//   MTU = 800 -> IP-payload room per frag = 800 - 20 = 780
//   Frag1 carries 780 bytes of L4-data -> frame size 14 + 20 + 780 + 4 = 818
//   Frag2 carries (1440 - 20 - 780) = 640 bytes of L4-data
//     wait: L4 datagram = TCP_HDR + APP_PAYLOAD = 1420. That is what IP fragments.
//     Frag1 IP-payload = 780  (covers TCP header + first 760 app bytes)
//     Frag2 IP-payload = 1420 - 780 = 640
//   Frag2 frame: 14 + 20 + 640 + 4 = 678
// Spec text used 660 + 680 numbers; close to but not equal real arithmetic.
// Use the real numbers; spec numbers were illustrative.
const FRAG1_PAYLOAD = 780;
const FRAG2_PAYLOAD = 1420 - FRAG1_PAYLOAD;
const FRAG1_FRAME_BYTES = ETH_HDR + IP_HDR + FRAG1_PAYLOAD + ETH_CRC; // 818
const FRAG2_FRAME_BYTES = ETH_HDR + IP_HDR + FRAG2_PAYLOAD + ETH_CRC; // 678

function mk(
  step: number,
  packets: Packet[],
  message: string,
  highlightLayer?: { side: Side; layer: Layer },
  event?: EventKind,
  reassemblyBuffer?: { fragments: Packet[] }
): Frame<State> {
  return {
    state: {
      step,
      packets,
      highlightLayer,
      event,
      reassemblyBuffer,
      message,
    },
    aria: message,
  };
}

function buildFrames(): Frame<State>[] {
  const frames: Frame<State>[] = [];

  // F0 — intro: app PDU at L7 sender
  frames.push(
    mk(
      0,
      [
        {
          id: "app",
          side: "SENDER",
          layer: "L7",
          payload: APP_PAYLOAD,
          headers: {},
          label: `HTTP ${APP_PAYLOAD}B`,
        },
      ],
      "Client app produces a 1400-byte HTTP GET. Stack will encapsulate it down to L1 wire bits.",
      { side: "SENDER", layer: "L7" },
      "init"
    )
  );

  // F1 — L7 to L6
  frames.push(
    mk(
      1,
      [
        {
          id: "app",
          side: "SENDER",
          layer: "L6",
          payload: APP_PAYLOAD,
          headers: {},
          label: `HTTP ${APP_PAYLOAD}B`,
        },
      ],
      "L7 → L6: Presentation hands the payload to Session. No header added here in our trace.",
      { side: "SENDER", layer: "L6" }
    )
  );

  // F2 — L6 to L5
  frames.push(
    mk(
      2,
      [
        {
          id: "app",
          side: "SENDER",
          layer: "L5",
          payload: APP_PAYLOAD,
          headers: {},
          label: `HTTP ${APP_PAYLOAD}B`,
        },
      ],
      "L6 → L5: Session layer. Still 1400 bytes, no transport framing yet.",
      { side: "SENDER", layer: "L5" }
    )
  );

  // F3 — L5 to L4 — add TCP header
  frames.push(
    mk(
      3,
      [
        {
          id: "app",
          side: "SENDER",
          layer: "L4",
          payload: APP_PAYLOAD,
          headers: { tcp: true },
          label: `TCP+HTTP ${APP_PAYLOAD + TCP_HDR}B`,
        },
      ],
      "L4 TCP: prepend 20-byte TCP header (seq, ack, flags, window). Segment is now 1420 bytes.",
      { side: "SENDER", layer: "L4" },
      "header-add"
    )
  );

  // F4 — L4 to L3 — add IP header
  frames.push(
    mk(
      4,
      [
        {
          id: "app",
          side: "SENDER",
          layer: "L3",
          payload: APP_PAYLOAD,
          headers: { tcp: true, ip: true },
          label: `IP+TCP+HTTP ${APP_PAYLOAD + TCP_HDR + IP_HDR}B`,
        },
      ],
      "L3 IP: prepend 20-byte IP header (src/dst, TTL, proto=TCP). Datagram is 1440 bytes.",
      { side: "SENDER", layer: "L3" },
      "header-add"
    )
  );

  // F5 — L3 MTU check → fragment
  frames.push(
    mk(
      5,
      [
        {
          id: "frag1",
          side: "SENDER",
          layer: "L3",
          payload: FRAG1_PAYLOAD,
          headers: { ip: true },
          fragInfo: { offset: 0, mf: 1, total: FRAG1_PAYLOAD + IP_HDR },
          label: `[F1 off=0 MF=1] ${FRAG1_PAYLOAD + IP_HDR}B`,
        },
        {
          id: "frag2",
          side: "SENDER",
          layer: "L3",
          payload: FRAG2_PAYLOAD,
          headers: { ip: true },
          fragInfo: {
            offset: FRAG1_PAYLOAD,
            mf: 0,
            total: FRAG2_PAYLOAD + IP_HDR,
          },
          label: `[F2 off=${FRAG1_PAYLOAD} MF=0] ${
            FRAG2_PAYLOAD + IP_HDR
          }B`,
        },
      ],
      `MTU=800 < 1440 — FRAGMENT. F1: 780B payload (fits exactly into 800B). F2: 640B payload + MF=0.`,
      { side: "SENDER", layer: "L3" },
      "fragment"
    )
  );

  // F6 — Frag1 to L2 — add Ethernet header + CRC
  frames.push(
    mk(
      6,
      [
        {
          id: "frag1",
          side: "SENDER",
          layer: "L2",
          payload: FRAG1_PAYLOAD,
          headers: { ip: true, eth: true, crc: true },
          fragInfo: { offset: 0, mf: 1, total: FRAG1_FRAME_BYTES },
          label: `ETH+F1+CRC ${FRAG1_FRAME_BYTES}B`,
        },
        {
          id: "frag2",
          side: "SENDER",
          layer: "L3",
          payload: FRAG2_PAYLOAD,
          headers: { ip: true },
          fragInfo: {
            offset: FRAG1_PAYLOAD,
            mf: 0,
            total: FRAG2_PAYLOAD + IP_HDR,
          },
          label: `[F2 waiting]`,
        },
      ],
      "L2 Ethernet wraps F1: 14B header + 4B CRC. Frame ready for the wire (818B total).",
      { side: "SENDER", layer: "L2" },
      "header-add"
    )
  );

  // F7 — Frag1 L2 to L1 — bits on wire
  frames.push(
    mk(
      7,
      [
        {
          id: "frag1",
          side: "SENDER",
          layer: "L1",
          payload: FRAG1_PAYLOAD,
          headers: { ip: true, eth: true, crc: true },
          fragInfo: { offset: 0, mf: 1, total: FRAG1_FRAME_BYTES },
          label: `F1 on wire ⇣`,
        },
        {
          id: "frag2",
          side: "SENDER",
          layer: "L3",
          payload: FRAG2_PAYLOAD,
          headers: { ip: true },
          fragInfo: {
            offset: FRAG1_PAYLOAD,
            mf: 0,
            total: FRAG2_PAYLOAD + IP_HDR,
          },
          label: `[F2 waiting]`,
        },
      ],
      "L1: bits travel as voltage/light on the medium. F1 traverses the wire from sender to receiver.",
      { side: "SENDER", layer: "L1" },
      "wire-transit"
    )
  );

  // F8 — Frag2 sent down + on wire too (collapse encapsulation steps for F2 to keep frame budget)
  frames.push(
    mk(
      8,
      [
        {
          id: "frag1",
          side: "RECEIVER",
          layer: "L1",
          payload: FRAG1_PAYLOAD,
          headers: { ip: true, eth: true, crc: true },
          fragInfo: { offset: 0, mf: 1, total: FRAG1_FRAME_BYTES },
          label: `F1 arrived`,
        },
        {
          id: "frag2",
          side: "SENDER",
          layer: "L1",
          payload: FRAG2_PAYLOAD,
          headers: { ip: true, eth: true, crc: true },
          fragInfo: {
            offset: FRAG1_PAYLOAD,
            mf: 0,
            total: FRAG2_FRAME_BYTES,
          },
          label: `F2 on wire ⇣`,
        },
      ],
      "F1 has arrived at receiver L1. F2 is now wrapped (ETH+IP+CRC) and traversing the wire.",
      { side: "SENDER", layer: "L1" },
      "wire-transit"
    )
  );

  // F9 — receiver L1 → L2 for Frag1 (CRC verified, strip Ethernet)
  frames.push(
    mk(
      9,
      [
        {
          id: "frag1",
          side: "RECEIVER",
          layer: "L2",
          payload: FRAG1_PAYLOAD,
          headers: { ip: true },
          fragInfo: { offset: 0, mf: 1, total: FRAG1_PAYLOAD + IP_HDR },
          label: `F1 ⇡ stripped ETH`,
        },
        {
          id: "frag2",
          side: "RECEIVER",
          layer: "L1",
          payload: FRAG2_PAYLOAD,
          headers: { ip: true, eth: true, crc: true },
          fragInfo: {
            offset: FRAG1_PAYLOAD,
            mf: 0,
            total: FRAG2_FRAME_BYTES,
          },
          label: `F2 arrived`,
        },
      ],
      "Receiver L2: CRC of F1 verifies. Strip 14B Ethernet header + 4B trailer. Hand IP datagram up.",
      { side: "RECEIVER", layer: "L2" },
      "header-strip"
    )
  );

  // F10 — receiver L2 → L3 for Frag1 (buffer in reassembly queue)
  frames.push(
    mk(
      10,
      [
        {
          id: "frag1",
          side: "RECEIVER",
          layer: "L3",
          payload: FRAG1_PAYLOAD,
          headers: { ip: true },
          fragInfo: { offset: 0, mf: 1, total: FRAG1_PAYLOAD + IP_HDR },
          label: `[F1 buf off=0]`,
        },
        {
          id: "frag2",
          side: "RECEIVER",
          layer: "L2",
          payload: FRAG2_PAYLOAD,
          headers: { ip: true },
          fragInfo: {
            offset: FRAG1_PAYLOAD,
            mf: 0,
            total: FRAG2_PAYLOAD + IP_HDR,
          },
          label: `F2 ⇡ stripped ETH`,
        },
      ],
      "L3 reassembly buffer: park F1 (offset=0, MF=1). Wait for the rest of the original datagram.",
      { side: "RECEIVER", layer: "L3" },
      "header-strip",
      {
        fragments: [
          {
            id: "frag1",
            side: "RECEIVER",
            layer: "L3",
            payload: FRAG1_PAYLOAD,
            headers: { ip: true },
            fragInfo: { offset: 0, mf: 1, total: FRAG1_PAYLOAD + IP_HDR },
            label: `F1`,
          },
        ],
      }
    )
  );

  // F11 — receiver L2 → L3 for Frag2 (combine in queue)
  frames.push(
    mk(
      11,
      [
        {
          id: "frag1",
          side: "RECEIVER",
          layer: "L3",
          payload: FRAG1_PAYLOAD,
          headers: { ip: true },
          fragInfo: { offset: 0, mf: 1, total: FRAG1_PAYLOAD + IP_HDR },
          label: `[F1 buf]`,
        },
        {
          id: "frag2",
          side: "RECEIVER",
          layer: "L3",
          payload: FRAG2_PAYLOAD,
          headers: { ip: true },
          fragInfo: {
            offset: FRAG1_PAYLOAD,
            mf: 0,
            total: FRAG2_PAYLOAD + IP_HDR,
          },
          label: `[F2 buf MF=0]`,
        },
      ],
      "F2 also arrives at L3 buffer (offset=780, MF=0). Both pieces of the original datagram are present.",
      { side: "RECEIVER", layer: "L3" },
      "header-strip",
      {
        fragments: [
          {
            id: "frag1",
            side: "RECEIVER",
            layer: "L3",
            payload: FRAG1_PAYLOAD,
            headers: { ip: true },
            fragInfo: { offset: 0, mf: 1, total: FRAG1_PAYLOAD + IP_HDR },
            label: `F1`,
          },
          {
            id: "frag2",
            side: "RECEIVER",
            layer: "L3",
            payload: FRAG2_PAYLOAD,
            headers: { ip: true },
            fragInfo: {
              offset: FRAG1_PAYLOAD,
              mf: 0,
              total: FRAG2_PAYLOAD + IP_HDR,
            },
            label: `F2`,
          },
        ],
      }
    )
  );

  // F12 — L3 reassembles
  frames.push(
    mk(
      12,
      [
        {
          id: "reassembled",
          side: "RECEIVER",
          layer: "L3",
          payload: APP_PAYLOAD,
          headers: { tcp: true, ip: true },
          label: `IP+TCP+HTTP ${APP_PAYLOAD + TCP_HDR + IP_HDR}B`,
        },
      ],
      "REASSEMBLE: MF=0 received → contiguous span 0..1419 present → produce single 1440B IP datagram.",
      { side: "RECEIVER", layer: "L3" },
      "reassemble"
    )
  );

  // F13 — strip IP + TCP headers progressively (L3 → L4 → L5)
  frames.push(
    mk(
      13,
      [
        {
          id: "reassembled",
          side: "RECEIVER",
          layer: "L4",
          payload: APP_PAYLOAD,
          headers: { tcp: true },
          label: `TCP+HTTP ${APP_PAYLOAD + TCP_HDR}B`,
        },
      ],
      "L3 → L4: strip IP header (1440 → 1420). L4 → L5: strip TCP header (1420 → 1400).",
      { side: "RECEIVER", layer: "L4" },
      "header-strip"
    )
  );

  // F14 — full app payload reaches L7 on receiver
  frames.push(
    mk(
      14,
      [
        {
          id: "reassembled",
          side: "RECEIVER",
          layer: "L7",
          payload: APP_PAYLOAD,
          headers: {},
          label: `HTTP ${APP_PAYLOAD}B`,
        },
      ],
      "L5 → L6 → L7: pass cleartext up. Server app sees the original 1400-byte HTTP GET, untouched.",
      { side: "RECEIVER", layer: "L7" }
    )
  );

  // F15 — summary
  frames.push(
    mk(
      15,
      [
        {
          id: "reassembled",
          side: "RECEIVER",
          layer: "L7",
          payload: APP_PAYLOAD,
          headers: {},
          label: `HTTP ${APP_PAYLOAD}B`,
        },
      ],
      "MTU fragmentation lives at L3 and is transparent to L4+. End-to-end principle preserved across the stack."
    )
  );

  return frames;
}

export const FRAMES = buildFrames();
export const FRAME_COUNT = FRAMES.length;

// --------- Layout constants ---------
const SVG_W = 480;
const SVG_H = 360;
void SVG_W;
void SVG_H;

const SENDER_X = 10;
const RECEIVER_X = 250;
const COL_W = 220;
const SEP_X = 240;

// Per-layer vertical zones (top edge & height) for layer rows.
// L7..L1 stacked top to bottom. Keep within y [10, 305].
type LayerSpec = { y: number; h: number; label: string };
const LAYER_SPECS: Record<Layer, LayerSpec> = {
  L7: { y: 15, h: 30, label: "L7 APP" },
  L6: { y: 45, h: 25, label: "L6 PRES" },
  L5: { y: 70, h: 25, label: "L5 SESS" },
  L4: { y: 95, h: 30, label: "L4 TCP" },
  L3: { y: 125, h: 35, label: "L3 IP" },
  L2: { y: 160, h: 35, label: "L2 ETH" },
  L1: { y: 195, h: 30, label: "L1 BITS" },
};

const WIRE_Y = 245;

const MSG_Y = 340;

// Map bytes to a packet width. Cap so a 1500B packet fits within a column.
const byteScale = scaleLinear<number>({
  domain: [0, 1500],
  range: [22, 175],
  clamp: true,
});

function packetWidth(bytes: number): number {
  return byteScale(bytes);
}

function layerBoxX(side: Side): number {
  return side === "SENDER" ? SENDER_X : RECEIVER_X;
}

// Position a packet inside its layer cell, horizontally centered (unless wire transit).
function packetPos(p: Packet): { x: number; y: number; w: number; h: number } {
  const spec = LAYER_SPECS[p.layer];
  const colX = layerBoxX(p.side);
  const bytesForWidth =
    p.fragInfo?.total ?? p.payload + headerOverhead(p.headers);
  const w = packetWidth(bytesForWidth);
  const h = Math.max(12, spec.h - 12);
  const cx = colX + COL_W / 2;
  const x = cx - w / 2;
  const y = spec.y + (spec.h - h) / 2;
  return { x, y, w, h };
}

function headerOverhead(h: Packet["headers"]): number {
  let n = 0;
  if (h.tcp) n += TCP_HDR;
  if (h.ip) n += IP_HDR;
  if (h.eth) n += ETH_HDR;
  if (h.crc) n += ETH_CRC;
  return n;
}

// Wire transit position: x interpolates from sender L1 right edge to
// receiver L1 left edge at y = WIRE_Y.
function wireTransitX(side: Side, w: number): number {
  if (side === "SENDER") {
    // start near sender L1 right edge but slid onto the wire
    return SENDER_X + COL_W - w - 6;
  }
  return RECEIVER_X + 6;
}

function PacketGlyph({ p, event }: { p: Packet; event?: EventKind }) {
  // For wire-transit, render packet near wire rather than inside the L1 box.
  const onWire = event === "wire-transit" && p.layer === "L1";
  let x: number, y: number, w: number, h: number;
  if (onWire) {
    const bytesForWidth = p.fragInfo?.total ?? p.payload + headerOverhead(p.headers);
    w = packetWidth(bytesForWidth);
    h = 14;
    y = WIRE_Y - h / 2;
    x = wireTransitX(p.side, w);
  } else {
    const pos = packetPos(p);
    x = pos.x;
    y = pos.y;
    w = pos.w;
    h = pos.h;
  }

  const fill = ACCENT;
  const stroke = INK;

  return (
    <motion.g
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      transition={{ type: "tween", duration: 0.35, ease: "easeInOut" }}
    >
      <motion.rect
        initial={false}
        animate={{ x, y, width: w, height: h }}
        transition={{ type: "tween", duration: 0.45, ease: "easeInOut" }}
        fill={fill}
        stroke={stroke}
        strokeWidth={1}
      />
      <motion.text
        initial={false}
        animate={{ x: x + w / 2, y: y + h / 2 + 3 }}
        transition={{ type: "tween", duration: 0.45, ease: "easeInOut" }}
        textAnchor="middle"
        fontFamily={FONT_FAMILY}
        fontSize={8}
        fontWeight={700}
        fill={INK}
      >
        {p.label}
      </motion.text>
    </motion.g>
  );
}

function LayerRowRect({
  side,
  layer,
  isActive,
}: {
  side: Side;
  layer: Layer;
  isActive: boolean;
}) {
  const spec = LAYER_SPECS[layer];
  const x = layerBoxX(side);
  return (
    <motion.rect
      x={x}
      y={spec.y}
      width={COL_W}
      height={spec.h}
      initial={false}
      animate={{ fill: isActive ? ACCENT : PAPER }}
      transition={{ type: "tween", duration: 0.35, ease: "easeInOut" }}
      stroke={INK}
      strokeWidth={1}
    />
  );
}

function LayerRowLabel({
  side,
  layer,
}: {
  side: Side;
  layer: Layer;
}) {
  const spec = LAYER_SPECS[layer];
  const x = layerBoxX(side);
  return (
    <text
      x={x + 6}
      y={spec.y + 11}
      fontFamily={FONT_FAMILY}
      fontSize={9}
      fontWeight={700}
      fill={INK}
      opacity={0.85}
      pointerEvents="none"
    >
      {spec.label}
    </text>
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
  // SVG concatenates tspan textContent without inter-tspan whitespace.
  // Ensure a wrap-induced word boundary survives screen-reader / textContent reads.
  if (line2) {
    line1 = line1 + " ";
  }
  return (
    <AnimatePresence initial={false}>
      <motion.text
        key={message}
        x={16}
        y={MSG_Y - 12}
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

const LAYERS_ORDER: Layer[] = ["L7", "L6", "L5", "L4", "L3", "L2", "L1"];

function renderFrame(frame: Frame<State>): ReactNode {
  const { packets, highlightLayer, event, message } = frame.state;

  const fragmentFlash =
    event === "fragment" || event === "reassemble";

  return (
    <>
      {/* SVG markers for arrows */}
      <defs>
        <marker
          id="osi-arrow-ink"
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
        <marker
          id="osi-arrow-accent"
          viewBox="0 0 10 10"
          refX="9"
          refY="5"
          markerWidth="7"
          markerHeight="7"
          orient="auto-start-reverse"
          markerUnits="userSpaceOnUse"
        >
          <path
            d="M 0 0 L 10 5 L 0 10 z"
            fill={ACCENT}
            stroke={INK}
            strokeWidth="0.5"
          />
        </marker>
      </defs>

      {/* Column headers */}
      <text
        x={SENDER_X + COL_W / 2}
        y={11}
        textAnchor="middle"
        fontFamily={FONT_FAMILY}
        fontSize={10}
        fontWeight={700}
        fill={INK}
      >
        SENDER
      </text>
      <text
        x={RECEIVER_X + COL_W / 2}
        y={11}
        textAnchor="middle"
        fontFamily={FONT_FAMILY}
        fontSize={10}
        fontWeight={700}
        fill={INK}
      >
        RECEIVER
      </text>

      {/* Vertical separator */}
      <line
        x1={SEP_X}
        y1={15}
        x2={SEP_X}
        y2={305}
        stroke={INK}
        strokeWidth={1}
        strokeDasharray="3 3"
        opacity={0.5}
      />

      {/* All 7 layer-row rectangles on each side (drawn FIRST so packets + labels paint on top) */}
      {LAYERS_ORDER.map((L) => (
        <LayerRowRect
          key={`s-rect-${L}`}
          side="SENDER"
          layer={L}
          isActive={
            !!highlightLayer &&
            highlightLayer.side === "SENDER" &&
            highlightLayer.layer === L
          }
        />
      ))}
      {LAYERS_ORDER.map((L) => (
        <LayerRowRect
          key={`r-rect-${L}`}
          side="RECEIVER"
          layer={L}
          isActive={
            !!highlightLayer &&
            highlightLayer.side === "RECEIVER" &&
            highlightLayer.layer === L
          }
        />
      ))}

      {/* Wire — dashed horizontal line between sender L1 and receiver L1 */}
      <line
        x1={SENDER_X + COL_W}
        y1={WIRE_Y}
        x2={RECEIVER_X}
        y2={WIRE_Y}
        stroke={INK}
        strokeWidth={1}
        strokeDasharray="4 3"
        opacity={0.7}
      />
      <text
        x={SENDER_X + COL_W + 4}
        y={WIRE_Y - 4}
        fontFamily={FONT_FAMILY}
        fontSize={8}
        fontStyle="italic"
        fill={INK}
        opacity={0.6}
      >
        wire
      </text>

      {/* MTU label sits BELOW the wire so it doesn't collide with the L1 BITS receiver-side row label */}
      <text
        x={SENDER_X + COL_W + 4}
        y={WIRE_Y + 12}
        fontFamily={FONT_FAMILY}
        fontSize={8}
        fontStyle="italic"
        fontWeight={700}
        fill={INK}
        opacity={0.75}
      >
        MTU = {MTU}B
      </text>

      {/* Fragment / reassemble flash band */}
      <AnimatePresence>
        {fragmentFlash && (
          <PopIn key={`flash-${event}`} durationMs={300}>
            <rect
              x={SENDER_X}
              y={LAYER_SPECS.L3.y - 2}
              width={COL_W + (SEP_X - (SENDER_X + COL_W)) + COL_W}
              height={LAYER_SPECS.L3.h + 4}
              fill={ACCENT}
              opacity={0.18}
              stroke="none"
            />
            <text
              x={SEP_X}
              y={LAYER_SPECS.L3.y - 4}
              textAnchor="middle"
              fontFamily={FONT_FAMILY}
              fontSize={9}
              fontWeight={700}
              fill={INK}
            >
              {event === "fragment" ? "FRAGMENT →" : "← REASSEMBLE"}
            </text>
          </PopIn>
        )}
      </AnimatePresence>

      {/* Packets */}
      <AnimatePresence>
        {packets.map((p) => (
          <PacketGlyph key={p.id + "-" + p.side + "-" + p.layer} p={p} event={event} />
        ))}
      </AnimatePresence>

      {/* Layer labels drawn AFTER packets so layer names stay legible when a chip occupies the row */}
      {LAYERS_ORDER.map((L) => (
        <LayerRowLabel key={`s-label-${L}`} side="SENDER" layer={L} />
      ))}
      {LAYERS_ORDER.map((L) => (
        <LayerRowLabel key={`r-label-${L}`} side="RECEIVER" layer={L} />
      ))}

      {/* Footer rect + message */}
      <rect
        x={10}
        y={MSG_Y - 28}
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

export function OsiEncap(): ReactNode {
  return (
    <AlgoStepperShell<State>
      title="RC-1 · OSI encapsulation + MTU fragmentation"
      desc="A 1400B HTTP GET travels DOWN the sender stack (TCP+IP+ETH headers added), L3 fragments at MTU=800, both fragments cross the wire, the receiver reassembles at L3 and strips headers UP to L7."
      frames={FRAMES}
      renderFrame={renderFrame}
      testIdPrefix="osi-encap"
    />
  );
}
