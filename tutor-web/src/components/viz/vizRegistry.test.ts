import { describe, it, expect } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { vizRegistry } from "./vizRegistry";

describe("vizRegistry", () => {
  it("covers exactly the ids in content/viz-ids.yaml", () => {
    const yaml = readFileSync(resolve(__dirname, "../../../../content/viz-ids.yaml"), "utf8");
    const ids = yaml
      .split("\n")
      .map((l) => l.match(/^\s*-\s*(\S+)\s*$/)?.[1])
      .filter((x): x is string => Boolean(x));
    expect(Object.keys(vizRegistry).sort()).toEqual([...ids].sort());
  });
});
