import { useEffect, type ReactNode, type SVGProps } from "react";
import {
  AnimatePresence,
  motion,
  useMotionValue,
  useSpring,
  useTransform,
} from "motion/react";

const DEFAULT_SPRING = { stiffness: 90, damping: 18, mass: 0.6 };

type SvgTextRest = Omit<SVGProps<SVGTextElement>, "children" | "format">;
type SvgLineRest = SVGProps<SVGLineElement>;
type SvgPathRest = SVGProps<SVGPathElement>;
type SvgGroupRest = SVGProps<SVGGElement>;

/**
 * TweenText — animates a numeric value smoothly via a spring.
 * Use anywhere a `<text>` element displays a number that changes between frames.
 *
 * The text content is driven by a MotionValue, so updates don't trigger React
 * reconciliation; the DOM textContent is mutated directly each animation frame.
 */
export function TweenText(props: {
  value: number;
  formatter?: (n: number) => string;
  spring?: { stiffness: number; damping: number; mass?: number };
} & SvgTextRest) {
  const { value, formatter = (n: number) => n.toFixed(0), spring = DEFAULT_SPRING, ...textProps } = props;
  const mv = useMotionValue(value);
  const sp = useSpring(mv, spring);
  const display = useTransform(sp, (v) => formatter(v));

  useEffect(() => {
    mv.set(value);
  }, [value, mv]);

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  return <motion.text {...(textProps as any)}>{display}</motion.text>;
}

/**
 * FadeText — cross-fades when the rendered string changes.
 * Use for non-numeric label updates where TweenText doesn't apply
 * (status text, mode badges, message bar copy, etc.).
 */
export function FadeText(props: {
  children: ReactNode;
  durationMs?: number;
} & SvgTextRest) {
  const { children, durationMs = 250, ...textProps } = props;
  return (
    <AnimatePresence mode="wait" initial={false}>
      <motion.text
        key={String(children)}
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        exit={{ opacity: 0 }}
        transition={{ duration: durationMs / 1000 }}
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        {...(textProps as any)}
      >
        {children}
      </motion.text>
    </AnimatePresence>
  );
}

/**
 * DrawLine — animates a straight line drawing on from start to end via
 * strokeDashoffset. Length is computed from x1/y1/x2/y2.
 */
export function DrawLine(props: {
  durationMs?: number;
  delayMs?: number;
} & SvgLineRest) {
  const { durationMs = 600, delayMs = 0, ...lineProps } = props;
  const x1 = Number(lineProps.x1 ?? 0);
  const y1 = Number(lineProps.y1 ?? 0);
  const x2 = Number(lineProps.x2 ?? 0);
  const y2 = Number(lineProps.y2 ?? 0);
  const len = Math.max(1, Math.hypot(x2 - x1, y2 - y1));
  return (
    <motion.line
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      {...(lineProps as any)}
      strokeDasharray={len}
      initial={{ strokeDashoffset: len, opacity: 0 }}
      animate={{ strokeDashoffset: 0, opacity: 1 }}
      transition={{
        strokeDashoffset: {
          duration: durationMs / 1000,
          delay: delayMs / 1000,
          ease: "easeInOut",
        },
        opacity: { duration: 0.15, delay: delayMs / 1000 },
      }}
    />
  );
}

/**
 * DrawPath — animates an SVG path drawing on via pathLength (0 → 1).
 * Use for curves, arrows, bezier paths, etc.
 */
export function DrawPath(props: {
  durationMs?: number;
  delayMs?: number;
} & SvgPathRest) {
  const { durationMs = 600, delayMs = 0, ...pathProps } = props;
  return (
    <motion.path
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      {...(pathProps as any)}
      initial={{ pathLength: 0, opacity: 0 }}
      animate={{ pathLength: 1, opacity: 1 }}
      transition={{
        pathLength: {
          duration: durationMs / 1000,
          delay: delayMs / 1000,
          ease: "easeInOut",
        },
        opacity: { duration: 0.15, delay: delayMs / 1000 },
      }}
    />
  );
}

/**
 * PopIn — fade-in on mount + fade-out on unmount.
 * Wrap conditional shapes/groups inside an AnimatePresence parent so the
 * exit animation fires before React unmounts the node.
 */
export function PopIn(props: {
  children: ReactNode;
  durationMs?: number;
  delayMs?: number;
} & SvgGroupRest) {
  const { children, durationMs = 350, delayMs = 0, ...gProps } = props;
  return (
    <motion.g
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      transition={{
        duration: durationMs / 1000,
        delay: delayMs / 1000,
        ease: "easeOut",
      }}
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      {...(gProps as any)}
    >
      {children}
    </motion.g>
  );
}

export { AnimatePresence, motion };
