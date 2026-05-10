import { test, expect } from "vitest";
import { deadlineUrgency, kindWeight, rankTask } from "../lib/taskRanking";

test("deadlineUrgency saturates at 0 (>=14d) and 1 (<=0d)", () => {
  const now = new Date("2026-05-10T00:00:00Z");
  expect(deadlineUrgency("2026-05-31T00:00:00Z", now)).toBe(0);
  expect(deadlineUrgency("2026-05-09T00:00:00Z", now)).toBe(1);
  const seven = deadlineUrgency("2026-05-17T00:00:00Z", now);
  expect(seven).toBeGreaterThan(0.4);
  expect(seven).toBeLessThan(0.6);
});

test("kindWeight matches keyword in title", () => {
  expect(kindWeight("Tema 5")).toBe(0.8);
  expect(kindWeight("Partial 2021")).toBe(1.0);
  expect(kindWeight("Lab 3")).toBe(0.5);
  expect(kindWeight("Seminar 2")).toBe(0.3);
  expect(kindWeight("Random thing")).toBe(0.4);
});

test("rankTask composite roughly matches spec weights", () => {
  const now = new Date("2026-05-10T00:00:00Z");
  const score = rankTask({ id: "1", subject: "PA", title: "Tema 5", deadline: "2026-05-17T00:00:00Z" }, 0.5, now);
  expect(score).toBeGreaterThan(0.5);
  expect(score).toBeLessThan(0.7);
});
