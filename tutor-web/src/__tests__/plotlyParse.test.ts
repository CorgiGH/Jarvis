import { test, expect } from "vitest";
import { parsePlotly } from "../lib/plotlyParse";

test("parsePlotly extracts ```plotly fenced JSON", () => {
  const text = "intro\n```plotly\n{\"data\":[{\"y\":[1,2,3]}]}\n```\noutro";
  const { body, plots } = parsePlotly(text);
  expect(plots).toHaveLength(1);
  expect(plots[0].json.data).toEqual([{ y: [1, 2, 3] }]);
  expect(body).toMatch(/intro\nPLOTLY0\noutro/);
});

test("parsePlotly handles multiple plots", () => {
  const text = "```plotly\n{\"data\":[]}\n```\nbetween\n```plotly\n{\"layout\":{}}\n```";
  const { plots } = parsePlotly(text);
  expect(plots).toHaveLength(2);
});

test("parsePlotly skips invalid JSON (leaves raw)", () => {
  const text = "```plotly\nnot json\n```";
  const { body, plots } = parsePlotly(text);
  expect(plots).toHaveLength(0);
  expect(body).toContain("not json");
});

test("parsePlotly no-op when no fences", () => {
  const { body, plots } = parsePlotly("plain text");
  expect(plots).toHaveLength(0);
  expect(body).toBe("plain text");
});
