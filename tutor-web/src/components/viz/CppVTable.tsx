import type { ReactNode } from "react";
import { AlgoStepperShell, type Frame } from "./AlgoStepperShell";
import { ACCENT, FONT_FAMILY, INK, PAPER } from "./theme";
import {
  AnimatePresence,
  DrawLine,
  DrawPath,
  FadeText,
  PopIn,
  TweenText,
  motion,
} from "./motion-helpers";

type Phase = 1 | 2;
type CodeLine = { text: string; highlighted: boolean };
type HeapObject = {
  id: string;
  className: string;
  fields: { name: string; value: string; pointer?: string }[];
  refcount?: number;
  vptrTarget?: string;
};
type VTable = { id: string; entries: { name: string; ptr: string }[] };
type Pointer = { from: string; to: string; kind: "shared" | "weak" | "raw" | "vptr" };
type CPPState = {
  step: number;
  phase: Phase;
  code: CodeLine[];
  heap: HeapObject[];
  vtables: VTable[];
  pointers: Pointer[];
  highlightedObjectId: string | null;
  highlightedPointerIdx: number | null;
  message: string;
  isLeak: boolean;
  showDispatch?: boolean;
};

function clone<T>(x: T): T {
  return JSON.parse(JSON.stringify(x));
}

function buildFrames(): Frame<CPPState>[] {
  const frames: Frame<CPPState>[] = [];

  const mk = (
    step: number,
    state: Omit<CPPState, "step">,
  ): Frame<CPPState> => ({
    state: { step, ...clone(state) },
    aria: state.message,
  });

  // ============ PHASE 1: vtable dispatch ============
  const code1: CodeLine[] = [
    { text: "class Animal { virtual void speak(); };", highlighted: false },
    { text: "class Dog : Animal { void speak() override; };", highlighted: false },
    { text: "class Cat : Animal { void speak() override; };", highlighted: false },
    { text: "Animal* a = new Dog();", highlighted: false },
    { text: "a->speak();", highlighted: false },
  ];

  const vtableDog: VTable = {
    id: "vtable-Dog",
    entries: [
      { name: "speak", ptr: "&Dog::speak" },
      { name: "~Dog", ptr: "&Dog::~Dog" },
    ],
  };
  const vtableCat: VTable = {
    id: "vtable-Cat",
    entries: [
      { name: "speak", ptr: "&Cat::speak" },
      { name: "~Cat", ptr: "&Cat::~Cat" },
    ],
  };
  const dogObject: HeapObject = {
    id: "obj-dog",
    className: "Dog",
    fields: [
      { name: "vptr", value: "-> vtable-Dog", pointer: "vtable-Dog" },
      { name: "name", value: '"Rex"' },
    ],
    vptrTarget: "vtable-Dog",
  };

  frames.push(mk(0, {
    phase: 1,
    code: code1.map((c, i) => ({ ...c, highlighted: i <= 2 })),
    heap: [],
    vtables: [vtableDog, vtableCat],
    pointers: [],
    highlightedObjectId: null,
    highlightedPointerIdx: null,
    message: "POO-1: class Animal has virtual speak(); Dog and Cat override it.",
    isLeak: false,
  }));

  frames.push(mk(1, {
    phase: 1,
    code: code1.map((c, i) => ({ ...c, highlighted: i === 3 })),
    heap: [dogObject],
    vtables: [vtableDog, vtableCat],
    pointers: [{ from: "ptr-a", to: "obj-dog", kind: "raw" }],
    highlightedObjectId: "obj-dog",
    highlightedPointerIdx: null,
    message: "Animal* a = new Dog(); heap allocation. a is Animal* (static type), points to Dog (dynamic type).",
    isLeak: false,
  }));

  frames.push(mk(2, {
    phase: 1,
    code: code1.map((c, i) => ({ ...c, highlighted: i === 3 })),
    heap: [dogObject],
    vtables: [vtableDog, vtableCat],
    pointers: [{ from: "ptr-a", to: "obj-dog", kind: "raw" }],
    highlightedObjectId: "obj-dog",
    highlightedPointerIdx: null,
    message: "Dog object layout: [vptr][name]. vptr is an implicit pointer to the vtable.",
    isLeak: false,
  }));

  frames.push(mk(3, {
    phase: 1,
    code: code1.map((c, i) => ({ ...c, highlighted: i === 3 })),
    heap: [dogObject],
    vtables: [vtableDog, vtableCat],
    pointers: [
      { from: "ptr-a", to: "obj-dog", kind: "raw" },
      { from: "obj-dog", to: "vtable-Dog", kind: "vptr" },
    ],
    highlightedObjectId: "obj-dog",
    highlightedPointerIdx: 1,
    message: "vptr points to Dog's vtable (read-only, shared by all Dog instances).",
    isLeak: false,
  }));

  frames.push(mk(4, {
    phase: 1,
    code: code1.map((c, i) => ({ ...c, highlighted: i === 4 })),
    heap: [dogObject],
    vtables: [vtableDog, vtableCat],
    pointers: [
      { from: "ptr-a", to: "obj-dog", kind: "raw" },
      { from: "obj-dog", to: "vtable-Dog", kind: "vptr" },
    ],
    highlightedObjectId: "obj-dog",
    highlightedPointerIdx: 1,
    message: "a->speak(); compiler doesn't know the dynamic type. Use virtual dispatch.",
    isLeak: false,
  }));

  frames.push(mk(5, {
    phase: 1,
    code: code1.map((c, i) => ({ ...c, highlighted: i === 4 })),
    heap: [dogObject],
    vtables: [vtableDog, vtableCat],
    pointers: [
      { from: "ptr-a", to: "obj-dog", kind: "raw" },
      { from: "obj-dog", to: "vtable-Dog", kind: "vptr" },
    ],
    highlightedObjectId: "obj-dog",
    highlightedPointerIdx: 1,
    message: "1) Deref a -> object; 2) read vptr -> vtable-Dog; 3) call vtable[0] = &Dog::speak.",
    isLeak: false,
    showDispatch: true,
  }));

  frames.push(mk(6, {
    phase: 1,
    code: code1.map((c, i) => ({ ...c, highlighted: i === 4 })),
    heap: [dogObject],
    vtables: [vtableDog, vtableCat],
    pointers: [
      { from: "ptr-a", to: "obj-dog", kind: "raw" },
      { from: "obj-dog", to: "vtable-Dog", kind: "vptr" },
    ],
    highlightedObjectId: null,
    highlightedPointerIdx: null,
    message: 'Dog::speak() runs -> prints "Woof". Polymorphism: O(1) dispatch via indirection.',
    isLeak: false,
    showDispatch: true,
  }));

  // ============ PHASE 2: shared_ptr cycle ============
  const code2: CodeLine[] = [
    { text: "struct A { shared_ptr<B> b; };", highlighted: false },
    { text: "struct B { shared_ptr<A> a; };", highlighted: false },
    { text: "auto pa = make_shared<A>();", highlighted: false },
    { text: "auto pb = make_shared<B>();", highlighted: false },
    { text: "pa->b = pb; pb->a = pa;", highlighted: false },
    { text: "// end scope; pa, pb destructed", highlighted: false },
  ];

  // Frame 7: A created, refcount=1
  frames.push(mk(7, {
    phase: 2,
    code: code2.map((c, i) => ({ ...c, highlighted: i === 2 })),
    heap: [
      { id: "obj-A", className: "A", fields: [{ name: "b", value: "(null)" }], refcount: 1 },
    ],
    vtables: [],
    pointers: [{ from: "ptr-pa", to: "obj-A", kind: "shared" }],
    highlightedObjectId: "obj-A",
    highlightedPointerIdx: null,
    message: "POO-4: auto pa = make_shared<A>(); A allocated, refcount=1, pa holds one shared_ptr.",
    isLeak: false,
  }));

  // Frame 8: B created, refcount=1
  frames.push(mk(8, {
    phase: 2,
    code: code2.map((c, i) => ({ ...c, highlighted: i === 3 })),
    heap: [
      { id: "obj-A", className: "A", fields: [{ name: "b", value: "(null)" }], refcount: 1 },
      { id: "obj-B", className: "B", fields: [{ name: "a", value: "(null)" }], refcount: 1 },
    ],
    vtables: [],
    pointers: [
      { from: "ptr-pa", to: "obj-A", kind: "shared" },
      { from: "ptr-pb", to: "obj-B", kind: "shared" },
    ],
    highlightedObjectId: "obj-B",
    highlightedPointerIdx: null,
    message: "auto pb = make_shared<B>(); B allocated, refcount=1, pb holds one shared_ptr.",
    isLeak: false,
  }));

  // Frame 9: cycle created
  frames.push(mk(9, {
    phase: 2,
    code: code2.map((c, i) => ({ ...c, highlighted: i === 4 })),
    heap: [
      { id: "obj-A", className: "A", fields: [{ name: "b", value: "-> B" }], refcount: 2 },
      { id: "obj-B", className: "B", fields: [{ name: "a", value: "-> A" }], refcount: 2 },
    ],
    vtables: [],
    pointers: [
      { from: "ptr-pa", to: "obj-A", kind: "shared" },
      { from: "ptr-pb", to: "obj-B", kind: "shared" },
      { from: "obj-A", to: "obj-B", kind: "shared" },
      { from: "obj-B", to: "obj-A", kind: "shared" },
    ],
    highlightedObjectId: null,
    highlightedPointerIdx: null,
    message: "pa->b = pb; pb->a = pa; CYCLE. A.refcount=2 (pa + B.a), B.refcount=2 (pb + A.b).",
    isLeak: false,
  }));

  // Frame 10: local scope ends
  frames.push(mk(10, {
    phase: 2,
    code: code2.map((c, i) => ({ ...c, highlighted: i === 5 })),
    heap: [
      { id: "obj-A", className: "A", fields: [{ name: "b", value: "-> B" }], refcount: 1 },
      { id: "obj-B", className: "B", fields: [{ name: "a", value: "-> A" }], refcount: 1 },
    ],
    vtables: [],
    pointers: [
      { from: "obj-A", to: "obj-B", kind: "shared" },
      { from: "obj-B", to: "obj-A", kind: "shared" },
    ],
    highlightedObjectId: null,
    highlightedPointerIdx: null,
    message: "end scope: pa, pb destructed -> A.refcount=1, B.refcount=1. Each held by the OTHER. Not destructed.",
    isLeak: true,
  }));

  // Frame 11: LEAK
  frames.push(mk(11, {
    phase: 2,
    code: code2.map((c, i) => ({ ...c, highlighted: i === 5 })),
    heap: [
      { id: "obj-A", className: "A", fields: [{ name: "b", value: "-> B" }], refcount: 1 },
      { id: "obj-B", className: "B", fields: [{ name: "a", value: "-> A" }], refcount: 1 },
    ],
    vtables: [],
    pointers: [
      { from: "obj-A", to: "obj-B", kind: "shared" },
      { from: "obj-B", to: "obj-A", kind: "shared" },
    ],
    highlightedObjectId: null,
    highlightedPointerIdx: null,
    message: "MEMORY LEAK. A + B unreachable from any local but refcount never hits 0 because of the cycle.",
    isLeak: true,
  }));

  // Frame 12: fix with weak_ptr
  frames.push(mk(12, {
    phase: 2,
    code: [
      { text: "struct A { shared_ptr<B> b; };", highlighted: false },
      { text: "struct B { weak_ptr<A> a; };   // weak!", highlighted: true },
      { text: "auto pa = make_shared<A>();", highlighted: false },
      { text: "auto pb = make_shared<B>();", highlighted: false },
      { text: "pa->b = pb; pb->a = pa;", highlighted: false },
      { text: "// end scope", highlighted: false },
    ],
    heap: [
      { id: "obj-A", className: "A", fields: [{ name: "b", value: "-> B" }], refcount: 1 },
      { id: "obj-B", className: "B", fields: [{ name: "a", value: "weak-> A" }], refcount: 1 },
    ],
    vtables: [],
    pointers: [
      { from: "ptr-pa", to: "obj-A", kind: "shared" },
      { from: "ptr-pb", to: "obj-B", kind: "shared" },
      { from: "obj-A", to: "obj-B", kind: "shared" },
      { from: "obj-B", to: "obj-A", kind: "weak" },
    ],
    highlightedObjectId: null,
    highlightedPointerIdx: 3,
    message: "Fix: struct B { weak_ptr<A> a; }. weak_ptr does NOT increment refcount.",
    isLeak: false,
  }));

  // Frame 13: clean destruction
  frames.push(mk(13, {
    phase: 2,
    code: [
      { text: "struct A { shared_ptr<B> b; };", highlighted: false },
      { text: "struct B { weak_ptr<A> a; };", highlighted: false },
      { text: "auto pa = make_shared<A>();", highlighted: false },
      { text: "auto pb = make_shared<B>();", highlighted: false },
      { text: "pa->b = pb; pb->a = pa;", highlighted: false },
      { text: "// end scope", highlighted: true },
    ],
    heap: [],
    vtables: [],
    pointers: [],
    highlightedObjectId: null,
    highlightedPointerIdx: null,
    message: "end scope: pa destructed -> A.refcount=0 -> A destructed -> A.b destructed -> B.refcount=0 -> B destructed. Clean.",
    isLeak: false,
  }));

  // Frame 14: conclusion
  frames.push(mk(14, {
    phase: 2,
    code: code2.map((c) => ({ ...c, highlighted: false })),
    heap: [],
    vtables: [],
    pointers: [],
    highlightedObjectId: null,
    highlightedPointerIdx: null,
    message: "Lesson: shared_ptr cycles can't be GC'd by refcount alone. Break cycles with weak_ptr.",
    isLeak: false,
  }));

  return frames;
}

export const FRAME_COUNT = 15;
export const FRAMES = buildFrames();

const SVG_W = 480;
const SVG_H = 360;

// Layout constants (used implicitly via the viewBox)
const _SVG_W: typeof SVG_W = SVG_W;
const _SVG_H: typeof SVG_H = SVG_H;
void _SVG_W;
void _SVG_H;

const CODE_X = 10;
const CODE_Y = 30;
const CODE_W = 220;
const CODE_H = 140;

const HEAP_X = 250;
const HEAP_Y = 30;
const HEAP_W = 220;
const HEAP_H = 250;

const MSG_Y_FOOTER = 340;

function objX(idx: number): number {
  return HEAP_X + 10 + idx * 105;
}
function objY(): number {
  return HEAP_Y + 20;
}

function renderFrame(frame: Frame<CPPState>): ReactNode {
  const {
    phase,
    code,
    heap,
    vtables,
    pointers,
    highlightedObjectId,
    highlightedPointerIdx,
    message,
    isLeak,
    showDispatch,
  } = frame.state;

  // Position heap objects
  const objPos = new Map<string, { x: number; y: number; w: number; h: number }>();
  heap.forEach((obj, i) => {
    const x = objX(i);
    const y = objY();
    const w = 95;
    const h = 24 + obj.fields.length * 16 + (obj.refcount !== undefined ? 14 : 0);
    objPos.set(obj.id, { x, y, w, h });
  });
  // vtables placed below heap
  vtables.forEach((vt, i) => {
    const x = HEAP_X + 10 + i * 105;
    const y = HEAP_Y + HEAP_H - 70;
    const w = 95;
    const h = 18 + vt.entries.length * 13;
    objPos.set(vt.id, { x, y, w, h });
  });

  // Pointer source/sink positions. stackPtrY y-aligns stack pointer
  // (pa/pb/a) to the vertical center of its current target so the line
  // stays level when targets pop in/out.
  function stackPtrY(name: string): number {
    const ptrId = `ptr-${name}`;
    const ptr = pointers.find((p) => p.from === ptrId);
    if (ptr) {
      const target = objPos.get(ptr.to);
      if (target) return target.y + target.h / 2;
    }
    const ix = ["pa", "pb", "a"].indexOf(name);
    return HEAP_Y + 40 + ix * 30;
  }

  function ptrPos(id: string, isFrom: boolean): { x: number; y: number } {
    if (id.startsWith("ptr-")) {
      const name = id.replace("ptr-", "");
      return { x: HEAP_X - 10, y: stackPtrY(name) };
    }
    const p = objPos.get(id);
    if (!p) return { x: 0, y: 0 };
    return { x: isFrom ? p.x + p.w : p.x, y: p.y + p.h / 2 };
  }

  return (
    <>
      {/* Arrowhead markers. orient="auto" rotates the marker to match the
          tangent of the path/line at its endpoint, so curved beziers get an
          arrowhead pointing along the curve (not stuck at 0/90/180 degrees).
          refX positions the marker tip exactly at the line endpoint. */}
      <defs>
        <marker
          id="cpp-arrow-ink"
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
          id="cpp-arrow-accent"
          viewBox="0 0 10 10"
          refX="9"
          refY="5"
          markerWidth="7"
          markerHeight="7"
          orient="auto-start-reverse"
          markerUnits="userSpaceOnUse"
        >
          <path d="M 0 0 L 10 5 L 0 10 z" fill={ACCENT} stroke={INK} strokeWidth="0.5" />
        </marker>
      </defs>

      {/* Phase indicator — cross-fades when phase swaps */}
      <FadeText
        x={CODE_X}
        y={20}
        fontFamily={FONT_FAMILY}
        fontSize={11}
        fontWeight={700}
        fill={INK}
        opacity={0.7}
      >
        {phase === 1
          ? "Phase 1: POO-1 virtual dispatch (vtable)"
          : "Phase 2: POO-4 shared_ptr ref-count cycle"}
      </FadeText>

      {/* Code pane (static frame) */}
      <rect
        x={CODE_X}
        y={CODE_Y}
        width={CODE_W}
        height={CODE_H}
        fill={PAPER}
        stroke={INK}
        strokeWidth={1}
      />
      <text
        x={CODE_X + 6}
        y={CODE_Y + 14}
        fontFamily={FONT_FAMILY}
        fontSize={9}
        fontWeight={700}
        fill={INK}
        opacity={0.6}
      >
        SOURCE
      </text>
      {/* Code line highlight rects — AnimatePresence so the yellow bar pops in/out */}
      <AnimatePresence>
        {code.map((line, i) => {
          if (!line.highlighted) return null;
          const y = CODE_Y + 30 + i * 18;
          return (
            <PopIn key={`code-hl-${phase}-${i}`} durationMs={250}>
              <rect
                x={CODE_X + 4}
                y={y - 12}
                width={CODE_W - 8}
                height={16}
                fill={ACCENT}
                opacity={0.7}
              />
            </PopIn>
          );
        })}
      </AnimatePresence>
      {/* Code line text — keyed by phase so text content swaps cleanly */}
      {code.map((line, i) => {
        const y = CODE_Y + 30 + i * 18;
        const text =
          line.text.length > 36 ? line.text.slice(0, 36) + "..." : line.text;
        return (
          <FadeText
            key={`code-${phase}-${i}-${text}`}
            x={CODE_X + 6}
            y={y}
            fontFamily={FONT_FAMILY}
            fontSize={9}
            fill={INK}
            durationMs={200}
          >
            {text}
          </FadeText>
        );
      })}

      {/* Heap pane (static frame) */}
      <rect
        x={HEAP_X}
        y={HEAP_Y}
        width={HEAP_W}
        height={HEAP_H}
        fill={PAPER}
        stroke={INK}
        strokeWidth={1}
      />
      <text
        x={HEAP_X + 6}
        y={HEAP_Y + 14}
        fontFamily={FONT_FAMILY}
        fontSize={9}
        fontWeight={700}
        fill={INK}
        opacity={0.6}
      >
        HEAP
      </text>
      {/* LEAK badge — pops in dramatically when refcount cycle leaves objects unreachable */}
      <AnimatePresence>
        {isLeak && (
          <PopIn key="leak-badge" durationMs={400}>
            <rect
              x={HEAP_X + HEAP_W - 50}
              y={HEAP_Y + 3}
              width={44}
              height={14}
              fill={INK}
              stroke={INK}
              strokeWidth={1}
            />
            <text
              x={HEAP_X + HEAP_W - 28}
              y={HEAP_Y + 13}
              textAnchor="middle"
              fontFamily={FONT_FAMILY}
              fontSize={9}
              fontWeight={700}
              fill={ACCENT}
            >
              LEAK
            </text>
          </PopIn>
        )}
      </AnimatePresence>

      {/* Stack pointer labels — appear/disappear via PopIn; motion.text inside follows
          the y as the target rect changes (stackPtrY recomputes per render). */}
      <AnimatePresence>
        {["pa", "pb"].map((name) => {
          const ptrId = `ptr-${name}`;
          const hasPtr = pointers.some((p) => p.from === ptrId);
          if (!hasPtr) return null;
          if (phase === 1 && name !== "pa") return null;
          const y = stackPtrY(name);
          const label = phase === 1 ? "a" : name;
          return (
            <PopIn key={`stk-${phase}-${name}`} durationMs={250}>
              <motion.text
                x={HEAP_X - 30}
                initial={false}
                animate={{ y: y + 4 }}
                transition={{ type: "tween", duration: 0.4, ease: "easeInOut" }}
                textAnchor="end"
                fontFamily={FONT_FAMILY}
                fontSize={9}
                fontWeight={700}
                fill={INK}
              >
                {label} {"->"}{" "}
              </motion.text>
            </PopIn>
          );
        })}
      </AnimatePresence>

      {/* Heap objects — PopIn on mount/unmount; persistent rects use motion.rect for highlight tween */}
      <AnimatePresence>
        {heap.map((obj) => {
          const p = objPos.get(obj.id);
          if (!p) return null;
          const highlighted = obj.id === highlightedObjectId;
          return (
            <PopIn key={`obj-${obj.id}`} durationMs={300}>
              <motion.rect
                x={p.x}
                y={p.y}
                width={p.w}
                height={p.h}
                initial={false}
                animate={{
                  fill: highlighted ? ACCENT : "#fff",
                  strokeWidth: highlighted ? 2 : 1,
                }}
                transition={{ duration: 0.35, ease: "easeInOut" }}
                stroke={INK}
              />
              <text
                x={p.x + p.w / 2}
                y={p.y + 13}
                textAnchor="middle"
                fontFamily={FONT_FAMILY}
                fontSize={9}
                fontWeight={700}
                fill={INK}
              >
                {obj.className}
              </text>
              <line
                x1={p.x}
                y1={p.y + 18}
                x2={p.x + p.w}
                y2={p.y + 18}
                stroke={INK}
                strokeWidth={0.5}
              />
              {obj.fields.map((f, i) => {
                const valShort =
                  f.value.length > 10 ? f.value.slice(0, 10) + "..." : f.value;
                return (
                  <FadeText
                    key={`f-${obj.id}-${i}-${f.value}`}
                    x={p.x + 4}
                    y={p.y + 30 + i * 14}
                    fontFamily={FONT_FAMILY}
                    fontSize={8}
                    fill={INK}
                    durationMs={200}
                  >
                    {f.name}: {valShort}
                  </FadeText>
                );
              })}
              {obj.refcount !== undefined && (
                <TweenText
                  x={p.x + p.w - 4}
                  y={p.y + p.h - 4}
                  textAnchor="end"
                  fontFamily={FONT_FAMILY}
                  fontSize={8}
                  fontWeight={700}
                  fill={INK}
                  value={obj.refcount}
                  formatter={(n) => `refcnt=${Math.round(n)}`}
                />
              )}
            </PopIn>
          );
        })}
      </AnimatePresence>

      {/* vtables — PopIn so the dashed boxes ease in alongside the heap allocation */}
      <AnimatePresence>
        {vtables.map((vt) => {
          const p = objPos.get(vt.id);
          if (!p) return null;
          return (
            <PopIn key={`vt-${vt.id}`} durationMs={300}>
              <rect
                x={p.x}
                y={p.y}
                width={p.w}
                height={p.h}
                fill="#fff"
                stroke={INK}
                strokeWidth={1}
                strokeDasharray="2 2"
              />
              <text
                x={p.x + p.w / 2}
                y={p.y + 11}
                textAnchor="middle"
                fontFamily={FONT_FAMILY}
                fontSize={8}
                fontWeight={700}
                fill={INK}
              >
                {vt.id.replace("vtable-", "vtable ")}
              </text>
              <line
                x1={p.x + 4}
                y1={p.y + 15}
                x2={p.x + p.w - 4}
                y2={p.y + 15}
                stroke={INK}
                strokeWidth={0.5}
                opacity={0.4}
              />
              {vt.entries.map((e, i) => (
                <text
                  key={`vt-e-${i}`}
                  x={p.x + 5}
                  y={p.y + 26 + i * 13}
                  fontFamily={FONT_FAMILY}
                  fontSize={8}
                  fill={INK}
                >
                  [{i}] {e.name}
                </text>
              ))}
            </PopIn>
          );
        })}
      </AnimatePresence>

      {/* Pointers — keyed by from->to so framer-motion animates endpoint changes.
          Stack→heap pointers route via bezier above the heap (arrows arc over any
          other objects between source and target). Cycle pairs (Phase 2) use
          larger ±35px arcOffset so the two curved edges don't collide with the
          objects they pass between. */}
      <AnimatePresence>
        {pointers.map((p, i) => {
          const fromSrc = ptrPos(p.from, true);
          const toSink = ptrPos(p.to, false);
          const highlighted = i === highlightedPointerIdx;
          const stroke = highlighted ? ACCENT : INK;
          const sw = highlighted ? 2 : 1;
          const dash =
            p.kind === "weak" ? "3 3" : p.kind === "vptr" ? "1 2" : undefined;

          const isStackPtr = p.from.startsWith("ptr-");
          const isObjectTarget = objPos.has(p.to);
          const hasReverse = pointers.some(
            (q) => q !== p && q.from === p.to && q.to === p.from,
          );

          // Compute the final geometric `from`/`to` and whether to use a bezier.
          let from = fromSrc;
          let to = toSink;
          let pathD: string | null = null;
          let midX = (from.x + to.x) / 2;
          let midY = (from.y + to.y) / 2;
          let arrowAngleDeg = 0; // 0 = points right (default arrow direction)

          if (isStackPtr && isObjectTarget) {
            // Arc OVER the heap. Land on the top edge of the target so the
            // arrow tip points downward into the object. Control-point y
            // scales with horizontal distance so short hops use a small arc
            // and long hops a tall one.
            const target = objPos.get(p.to)!;
            const targetCx = target.x + target.w / 2;
            const dx = Math.abs(targetCx - from.x);
            const arcHeight = Math.min(40, 16 + dx * 0.18);
            to = { x: targetCx, y: target.y };
            midX = (from.x + to.x) / 2;
            midY = Math.min(from.y, to.y) - arcHeight;
            pathD = `M ${from.x} ${from.y} Q ${midX} ${midY} ${to.x} ${to.y}`;
            arrowAngleDeg = 90; // tip points down
          } else if (objPos.has(p.from) && isObjectTarget) {
            // Object → object. If targets sit far below/above the source
            // (e.g. obj → vtable), route vertically so the arrow doesn't
            // cut diagonally across the heap and through other objects'
            // text. Use the centers of top/bottom edges so the arrow lands
            // cleanly on the boundary.
            const src = objPos.get(p.from)!;
            const tgt = objPos.get(p.to)!;
            const srcCx = src.x + src.w / 2;
            const tgtCx = tgt.x + tgt.w / 2;
            const srcCy = src.y + src.h / 2;
            const tgtCy = tgt.y + tgt.h / 2;
            const vGap = Math.abs(tgtCy - srcCy);
            const hGap = Math.abs(tgtCx - srcCx);

            if (vGap > hGap && vGap > 40) {
              // Vertical layout — go bottom-to-top or top-to-bottom.
              const goingDown = tgtCy > srcCy;
              from = { x: srcCx, y: goingDown ? src.y + src.h : src.y };
              to = { x: tgtCx, y: goingDown ? tgt.y : tgt.y + tgt.h };
              arrowAngleDeg = goingDown ? 90 : 270;
              if (Math.abs(tgtCx - srcCx) > 4) {
                // Slight S-curve when x's differ — vertical control points
                // keep the path mostly vertical with a gentle bend.
                const midY2 = (from.y + to.y) / 2;
                pathD = `M ${from.x} ${from.y} C ${from.x} ${midY2}, ${to.x} ${midY2}, ${to.x} ${to.y}`;
              } else {
                // Pure vertical line.
                pathD = `M ${from.x} ${from.y} L ${to.x} ${to.y}`;
              }
              midX = (from.x + to.x) / 2;
              midY = (from.y + to.y) / 2;
            } else if (hasReverse) {
              // Phase 2 cycle pair — pick the edge closest to the target so
              // the line stays in the gap between the objects, then arc
              // above for forward and below for reverse.
              const src = objPos.get(p.from)!;
              const tgt = objPos.get(p.to)!;
              const forward = tgt.x > src.x;
              from = {
                x: forward ? src.x + src.w : src.x,
                y: src.y + src.h / 2,
              };
              to = {
                x: forward ? tgt.x : tgt.x + tgt.w,
                y: tgt.y + tgt.h / 2,
              };
              midX = (from.x + to.x) / 2;
              midY = (from.y + to.y) / 2 + (forward ? -45 : 45);
              pathD = `M ${from.x} ${from.y} Q ${midX} ${midY} ${to.x} ${to.y}`;
              arrowAngleDeg = forward ? 0 : 180;
            }
          } else if (hasReverse) {
            // Generic object↔object reverse-pair (same-y siblings).
            const src = objPos.get(p.from);
            const tgt = objPos.get(p.to);
            if (src && tgt) {
              const forward = tgt.x > src.x;
              from = {
                x: forward ? src.x + src.w : src.x,
                y: src.y + src.h / 2,
              };
              to = {
                x: forward ? tgt.x : tgt.x + tgt.w,
                y: tgt.y + tgt.h / 2,
              };
              midX = (from.x + to.x) / 2;
              midY = (from.y + to.y) / 2 + (forward ? -45 : 45);
              pathD = `M ${from.x} ${from.y} Q ${midX} ${midY} ${to.x} ${to.y}`;
              arrowAngleDeg = forward ? 0 : 180;
            }
          }

          const key = `ptr-${p.from}->${p.to}-${p.kind}`;
          void arrowAngleDeg; // not used now that SVG markers handle orientation
          // SVG <marker orient="auto"> rotates the arrowhead to match the
          // path/line tangent at its endpoint, so it stays glued to the line
          // along curves and straight diagonals alike.
          const markerEnd =
            stroke === ACCENT ? "url(#cpp-arrow-accent)" : "url(#cpp-arrow-ink)";

          return (
            <PopIn key={key} durationMs={300}>
              {pathD ? (
                <DrawPath
                  d={pathD}
                  fill="none"
                  stroke={stroke}
                  strokeWidth={sw}
                  strokeDasharray={dash}
                  durationMs={500}
                  markerEnd={markerEnd}
                />
              ) : (
                <motion.line
                  initial={false}
                  animate={{
                    x1: from.x,
                    y1: from.y,
                    x2: to.x,
                    y2: to.y,
                    stroke,
                  }}
                  transition={{ type: "tween", duration: 0.45, ease: "easeInOut" }}
                  strokeWidth={sw}
                  strokeDasharray={dash}
                  markerEnd={markerEnd}
                />
              )}
              {p.kind === "weak" && (
                <text
                  x={midX}
                  y={midY - 4}
                  textAnchor="middle"
                  fontFamily={FONT_FAMILY}
                  fontSize={7}
                  fontWeight={700}
                  fill={INK}
                >
                  weak
                </text>
              )}
            </PopIn>
          );
        })}
      </AnimatePresence>

      {/* Phase-1 polymorphic dispatch resolution — three staggered DrawLines
          tracing the lookup path: a -> object -> vptr -> vtable slot. */}
      <AnimatePresence>
        {phase === 1 && showDispatch && (() => {
          const dog = objPos.get("obj-dog");
          const vt = objPos.get("vtable-Dog");
          if (!dog || !vt) return null;
          const aY = stackPtrY("pa");
          // 1) a -> object body
          const step1 = {
            x1: HEAP_X - 10,
            y1: aY,
            x2: dog.x + dog.w / 2,
            y2: dog.y + dog.h / 2,
          };
          // 2) object vptr field -> vtable header
          const step2 = {
            x1: dog.x + dog.w / 2,
            y1: dog.y + 30,
            x2: vt.x + vt.w / 2,
            y2: vt.y,
          };
          // 3) vtable speak slot -> conceptual function below the vtable
          // (placed below so the label doesn't overlap the adjacent vtable-Cat).
          const labelY = vt.y + vt.h + 14;
          const step3 = {
            x1: vt.x + vt.w / 2,
            y1: vt.y + vt.h,
            x2: vt.x + vt.w / 2,
            y2: labelY - 6,
          };
          return (
            <PopIn key="dispatch-resolution" durationMs={200}>
              <DrawLine
                {...step1}
                stroke={ACCENT}
                strokeWidth={2}
                durationMs={350}
                delayMs={0}
              />
              <DrawLine
                {...step2}
                stroke={ACCENT}
                strokeWidth={2}
                durationMs={350}
                delayMs={350}
              />
              <DrawLine
                {...step3}
                stroke={ACCENT}
                strokeWidth={2}
                durationMs={300}
                delayMs={700}
              />
              <text
                x={vt.x + vt.w / 2}
                y={labelY}
                textAnchor="middle"
                fontFamily={FONT_FAMILY}
                fontSize={8}
                fontWeight={700}
                fill={INK}
              >
                Dog::speak
              </text>
            </PopIn>
          );
        })()}
      </AnimatePresence>

      {/* Footer message (2-line wrap, fade-on-change) */}
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

export function CppVTable(): ReactNode {
  return (
    <AlgoStepperShell<CPPState>
      title="POO-1/POO-4 C++ vtable + smart_ptr ref-count cycle"
      desc="Phase 1: virtual dispatch via vptr/vtable. Phase 2: shared_ptr cycle -> memory leak; fix with weak_ptr."
      frames={FRAMES}
      renderFrame={renderFrame}
      testIdPrefix="cpp-vtable"
    />
  );
}
