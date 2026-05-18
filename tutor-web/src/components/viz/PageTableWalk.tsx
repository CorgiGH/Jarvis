import type { ReactNode } from "react";
import { AlgoStepperShell, type Frame } from "./AlgoStepperShell";
import { ACCENT, FONT_FAMILY, INK, PAPER } from "./theme";

type PTE = {
  vpn: number;
  valid: boolean;
  dirty: boolean;
  pfn: number | null;
  cow: boolean;
  writable: boolean;
};
type TLBEntry = { vpn: number; pfn: number };
type PhysFrame = { pfn: number; allocated: boolean; cowShared: boolean };
type PageTableState = {
  phase: 1 | 2 | 3 | 4;
  va: { vpn: number; offset: number };
  pa: { pfn: number; offset: number } | null;
  tlb: TLBEntry[];
  pageTable: PTE[];
  physMem: PhysFrame[];
  highlightedPte: number | null;
  highlightedTlb: number | null;
  highlightedPhys: number | null;
  message: string;
};

const NUM_PT_ENTRIES = 8;
const NUM_PHYS_FRAMES = 8;
const NUM_TLB_ENTRIES = 4;

function initialPT(): PTE[] {
  return Array.from({ length: NUM_PT_ENTRIES }, (_, i) => ({
    vpn: i,
    valid: false,
    dirty: false,
    pfn: null,
    cow: false,
    writable: true,
  }));
}

function initialPhys(): PhysFrame[] {
  return Array.from({ length: NUM_PHYS_FRAMES }, (_, i) => ({
    pfn: i,
    allocated: false,
    cowShared: false,
  }));
}

function clone<T>(x: T): T {
  return JSON.parse(JSON.stringify(x));
}

function buildFrames(): Frame<PageTableState>[] {
  const frames: Frame<PageTableState>[] = [];
  // Set up the initial state: pages 2,3,5 are mapped → frames 1,2,4
  const pt = initialPT();
  pt[2] = { vpn: 2, valid: true, dirty: false, pfn: 1, cow: false, writable: true };
  pt[3] = { vpn: 3, valid: true, dirty: false, pfn: 2, cow: false, writable: true };
  pt[5] = { vpn: 5, valid: true, dirty: false, pfn: 4, cow: false, writable: true };
  const phys = initialPhys();
  phys[1].allocated = true;
  phys[2].allocated = true;
  phys[4].allocated = true;
  let tlb: TLBEntry[] = [];

  const push = (s: PageTableState) => frames.push({ state: clone(s), aria: s.message });

  // ===== PHASE 1: TLB miss + page table walk (5 frames) =====
  const va1 = { vpn: 3, offset: 0xF00 };

  push({
    phase: 1,
    va: va1,
    pa: null,
    tlb: clone(tlb),
    pageTable: clone(pt),
    physMem: clone(phys),
    highlightedPte: null,
    highlightedTlb: null,
    highlightedPhys: null,
    message: `Phase 1: VA = vpn=${va1.vpn}, offset=0x${va1.offset.toString(16)}. Check TLB...`,
  });
  push({
    phase: 1,
    va: va1,
    pa: null,
    tlb: clone(tlb),
    pageTable: clone(pt),
    physMem: clone(phys),
    highlightedPte: null,
    highlightedTlb: null,
    highlightedPhys: null,
    message: `TLB MISS for vpn=${va1.vpn}. Walk page table.`,
  });
  push({
    phase: 1,
    va: va1,
    pa: null,
    tlb: clone(tlb),
    pageTable: clone(pt),
    physMem: clone(phys),
    highlightedPte: va1.vpn,
    highlightedTlb: null,
    highlightedPhys: null,
    message: `PTE[${va1.vpn}] is valid. pfn=${pt[va1.vpn].pfn}.`,
  });
  tlb = [{ vpn: va1.vpn, pfn: pt[va1.vpn].pfn! }];
  push({
    phase: 1,
    va: va1,
    pa: null,
    tlb: clone(tlb),
    pageTable: clone(pt),
    physMem: clone(phys),
    highlightedPte: va1.vpn,
    highlightedTlb: 0,
    highlightedPhys: null,
    message: `Fill TLB: vpn=${va1.vpn} → pfn=${pt[va1.vpn].pfn}.`,
  });
  push({
    phase: 1,
    va: va1,
    pa: { pfn: pt[va1.vpn].pfn!, offset: va1.offset },
    tlb: clone(tlb),
    pageTable: clone(pt),
    physMem: clone(phys),
    highlightedPte: null,
    highlightedTlb: 0,
    highlightedPhys: pt[va1.vpn].pfn,
    message: `PA = pfn=${pt[va1.vpn].pfn}, offset=0x${va1.offset.toString(16)}. Read frame.`,
  });

  // ===== PHASE 2: TLB hit (3 frames) =====
  push({
    phase: 2,
    va: va1,
    pa: null,
    tlb: clone(tlb),
    pageTable: clone(pt),
    physMem: clone(phys),
    highlightedPte: null,
    highlightedTlb: null,
    highlightedPhys: null,
    message: `Phase 2: same VA again. Check TLB.`,
  });
  push({
    phase: 2,
    va: va1,
    pa: null,
    tlb: clone(tlb),
    pageTable: clone(pt),
    physMem: clone(phys),
    highlightedPte: null,
    highlightedTlb: 0,
    highlightedPhys: null,
    message: `TLB HIT! pfn=${tlb[0].pfn} found.`,
  });
  push({
    phase: 2,
    va: va1,
    pa: { pfn: tlb[0].pfn, offset: va1.offset },
    tlb: clone(tlb),
    pageTable: clone(pt),
    physMem: clone(phys),
    highlightedPte: null,
    highlightedTlb: 0,
    highlightedPhys: tlb[0].pfn,
    message: `PA resolved via fast path.`,
  });

  // ===== PHASE 3: page fault (6 frames) =====
  const va2 = { vpn: 6, offset: 0x100 };
  push({
    phase: 3,
    va: va2,
    pa: null,
    tlb: clone(tlb),
    pageTable: clone(pt),
    physMem: clone(phys),
    highlightedPte: null,
    highlightedTlb: null,
    highlightedPhys: null,
    message: `Phase 3: NEW va vpn=${va2.vpn}. TLB miss, walk PT.`,
  });
  push({
    phase: 3,
    va: va2,
    pa: null,
    tlb: clone(tlb),
    pageTable: clone(pt),
    physMem: clone(phys),
    highlightedPte: va2.vpn,
    highlightedTlb: null,
    highlightedPhys: null,
    message: `PTE[${va2.vpn}] valid=false. PAGE FAULT!`,
  });
  push({
    phase: 3,
    va: va2,
    pa: null,
    tlb: clone(tlb),
    pageTable: clone(pt),
    physMem: clone(phys),
    highlightedPte: va2.vpn,
    highlightedTlb: null,
    highlightedPhys: 3,
    message: `OS handler: allocate free frame (pfn=3).`,
  });
  pt[va2.vpn] = { vpn: va2.vpn, valid: true, dirty: false, pfn: 3, cow: false, writable: true };
  phys[3].allocated = true;
  push({
    phase: 3,
    va: va2,
    pa: null,
    tlb: clone(tlb),
    pageTable: clone(pt),
    physMem: clone(phys),
    highlightedPte: va2.vpn,
    highlightedTlb: null,
    highlightedPhys: 3,
    message: `Fill PTE[${va2.vpn}]: valid=1, pfn=3.`,
  });
  push({
    phase: 3,
    va: va2,
    pa: { pfn: 3, offset: va2.offset },
    tlb: clone(tlb),
    pageTable: clone(pt),
    physMem: clone(phys),
    highlightedPte: null,
    highlightedTlb: null,
    highlightedPhys: 3,
    message: `Retry translation. PA = pfn=3, offset=0x${va2.offset.toString(16)}.`,
  });

  // ===== PHASE 4: COW after fork (6 frames) =====
  push({
    phase: 4,
    va: { vpn: 3, offset: 0 },
    pa: null,
    tlb: clone(tlb),
    pageTable: clone(pt),
    physMem: clone(phys),
    highlightedPte: null,
    highlightedTlb: null,
    highlightedPhys: null,
    message: `Phase 4: fork(). Parent's pages now SHARED with child.`,
  });
  pt.forEach((e) => {
    if (e.valid) {
      e.cow = true;
      e.writable = false;
    }
  });
  phys.forEach((f) => {
    if (f.allocated) f.cowShared = true;
  });
  push({
    phase: 4,
    va: { vpn: 3, offset: 0 },
    pa: null,
    tlb: clone(tlb),
    pageTable: clone(pt),
    physMem: clone(phys),
    highlightedPte: null,
    highlightedTlb: null,
    highlightedPhys: null,
    message: `All PTEs marked COW (read-only).`,
  });
  push({
    phase: 4,
    va: { vpn: 3, offset: 0 },
    pa: null,
    tlb: clone(tlb),
    pageTable: clone(pt),
    physMem: clone(phys),
    highlightedPte: 3,
    highlightedTlb: null,
    highlightedPhys: 2,
    message: `Parent WRITES to vpn=3 (COW). Page-fault!`,
  });
  push({
    phase: 4,
    va: { vpn: 3, offset: 0 },
    pa: null,
    tlb: clone(tlb),
    pageTable: clone(pt),
    physMem: clone(phys),
    highlightedPte: 3,
    highlightedTlb: null,
    highlightedPhys: 5,
    message: `OS: allocate new frame pfn=5, copy content from pfn=2.`,
  });
  pt[3].pfn = 5;
  pt[3].cow = false;
  pt[3].writable = true;
  phys[5].allocated = true;
  phys[5].cowShared = false;
  push({
    phase: 4,
    va: { vpn: 3, offset: 0 },
    pa: null,
    tlb: clone(tlb),
    pageTable: clone(pt),
    physMem: clone(phys),
    highlightedPte: 3,
    highlightedTlb: null,
    highlightedPhys: 5,
    message: `Parent's PTE[3] → pfn=5, writable. Child still on pfn=2.`,
  });
  push({
    phase: 4,
    va: { vpn: 3, offset: 0 },
    pa: { pfn: 5, offset: 0 },
    tlb: clone(tlb),
    pageTable: clone(pt),
    physMem: clone(phys),
    highlightedPte: null,
    highlightedTlb: null,
    highlightedPhys: 5,
    message: `Parent write succeeds.`,
  });

  return frames;
}

const FRAMES = buildFrames();

// Export frame count for tests
export const FRAME_COUNT = FRAMES.length;

// Layout constants — 480 × 360
const VA_BAR_X = 30;
const VA_BAR_Y = 20;
const VA_BAR_W = 420;
const VA_BAR_H = 20;

const TLB_X = 10;
const TLB_Y = 60;
const TLB_CELL_W = 80;
const TLB_CELL_H = 18;

const PT_X = 140;
const PT_Y = 60;
const PT_CELL_W = 160;
const PT_CELL_H = 22;

const PHYS_X = 350;
const PHYS_Y = 60;
const PHYS_CELL_W = 120;
const PHYS_CELL_H = 22;

const MSG_Y = 340;

function renderFrame(frame: Frame<PageTableState>): ReactNode {
  const {
    phase,
    va,
    pa,
    tlb,
    pageTable,
    physMem,
    highlightedPte,
    highlightedTlb,
    highlightedPhys,
    message,
  } = frame.state;

  return (
    <>
      {/* Phase indicator */}
      <text
        x={VA_BAR_X}
        y={VA_BAR_Y - 6}
        fontFamily={FONT_FAMILY}
        fontSize={9}
        fontWeight={700}
        fill={INK}
        opacity={0.7}
      >
        Phase {phase}
        {phase === 1
          ? ": TLB miss → walk"
          : phase === 2
          ? ": TLB hit"
          : phase === 3
          ? ": page fault"
          : ": COW after fork"}
      </text>

      {/* VA bar */}
      <rect
        x={VA_BAR_X}
        y={VA_BAR_Y}
        width={VA_BAR_W}
        height={VA_BAR_H}
        fill={PAPER}
        stroke={INK}
        strokeWidth={1}
      />
      <text
        x={VA_BAR_X + 6}
        y={VA_BAR_Y + 14}
        fontFamily={FONT_FAMILY}
        fontSize={10}
        fill={INK}
      >
        VA: vpn={va.vpn} · offset=0x{va.offset.toString(16).padStart(3, "0")}
      </text>
      {pa && (
        <text
          x={VA_BAR_X + VA_BAR_W - 6}
          y={VA_BAR_Y + 14}
          textAnchor="end"
          fontFamily={FONT_FAMILY}
          fontSize={10}
          fontWeight={700}
          fill={INK}
        >
          → PA: pfn={pa.pfn} · offset=0x{pa.offset.toString(16).padStart(3, "0")}
        </text>
      )}

      {/* Pane labels */}
      <text
        x={TLB_X}
        y={TLB_Y - 4}
        fontFamily={FONT_FAMILY}
        fontSize={9}
        fontWeight={700}
        fill={INK}
        opacity={0.7}
      >
        TLB
      </text>
      <text
        x={PT_X}
        y={PT_Y - 4}
        fontFamily={FONT_FAMILY}
        fontSize={9}
        fontWeight={700}
        fill={INK}
        opacity={0.7}
      >
        PAGE TABLE
      </text>
      <text
        x={PHYS_X}
        y={PHYS_Y - 4}
        fontFamily={FONT_FAMILY}
        fontSize={9}
        fontWeight={700}
        fill={INK}
        opacity={0.7}
      >
        PHYS FRAMES
      </text>

      {/* TLB entries */}
      {Array.from({ length: NUM_TLB_ENTRIES }, (_, i) => {
        const entry = tlb[i];
        const y = TLB_Y + i * (TLB_CELL_H + 2);
        const highlighted = highlightedTlb === i;
        return (
          <g key={`tlb-${i}`}>
            <rect
              x={TLB_X}
              y={y}
              width={TLB_CELL_W}
              height={TLB_CELL_H}
              fill={highlighted ? ACCENT : entry ? "#fff" : PAPER}
              stroke={INK}
              strokeWidth={highlighted ? 2 : 1}
            />
            <text
              x={TLB_X + 4}
              y={y + 13}
              fontFamily={FONT_FAMILY}
              fontSize={9}
              fill={INK}
            >
              {entry ? `vpn ${entry.vpn} → pfn ${entry.pfn}` : "—"}
            </text>
          </g>
        );
      })}

      {/* Page table entries */}
      {pageTable.map((pte, i) => {
        const y = PT_Y + i * (PT_CELL_H + 2);
        const highlighted = highlightedPte === i;
        return (
          <g key={`pte-${i}`}>
            <rect
              x={PT_X}
              y={y}
              width={PT_CELL_W}
              height={PT_CELL_H}
              fill={highlighted ? ACCENT : pte.valid ? "#fff" : PAPER}
              stroke={INK}
              strokeWidth={highlighted ? 2 : 1}
            />
            <text
              x={PT_X + 4}
              y={y + 14}
              fontFamily={FONT_FAMILY}
              fontSize={9}
              fill={INK}
            >
              [{i}]{" "}
              {pte.valid ? `v=1 pfn=${pte.pfn}` : "v=0"}{" "}
              {pte.cow ? "COW" : pte.valid ? (pte.writable ? "R/W" : "R") : ""}
            </text>
          </g>
        );
      })}

      {/* Physical frames */}
      {physMem.map((f, i) => {
        const y = PHYS_Y + i * (PHYS_CELL_H + 2);
        const highlighted = highlightedPhys === i;
        return (
          <g key={`phys-${i}`}>
            <rect
              x={PHYS_X}
              y={y}
              width={PHYS_CELL_W}
              height={PHYS_CELL_H}
              fill={
                highlighted ? ACCENT : f.allocated ? "#fff" : PAPER
              }
              stroke={INK}
              strokeWidth={highlighted ? 2 : 1}
            />
            <text
              x={PHYS_X + 4}
              y={y + 14}
              fontFamily={FONT_FAMILY}
              fontSize={9}
              fill={INK}
            >
              pfn {i}{" "}
              {f.allocated
                ? f.cowShared
                  ? "SHARED"
                  : "used"
                : "free"}
            </text>
          </g>
        );
      })}

      {/* Footer message (2-line wrap) */}
      <rect x={10} y={MSG_Y - 28} width={460} height={36} fill={PAPER} stroke={INK} strokeWidth={1} />
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
          <text x={16} y={MSG_Y - 12} fontFamily={FONT_FAMILY} fontSize={9} fontWeight={700} fill={INK}>
            <tspan x={16} dy={0}>{line1}</tspan>
            {line2 && <tspan x={16} dy={12}>{line2}</tspan>}
          </text>
        );
      })()}

      {/* Bounds anchor */}
      <rect x={0} y={0} width={480} height={360} fill="none" stroke="none" />
    </>
  );
}

export function PageTableWalk(): ReactNode {
  return (
    <AlgoStepperShell<PageTableState>
      title="SO-4 · Page table walk + TLB + page fault + COW"
      desc="Virtual address translation through TLB → page table → physical memory. Includes TLB miss + hit + page fault + copy-on-write after fork."
      frames={FRAMES}
      renderFrame={renderFrame}
      testIdPrefix="page-table-walk"
    />
  );
}
