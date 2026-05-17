import { test, expect } from "vitest";
import { formatEnum } from "../lib/formatEnum";

test("snake_case → space-separated lowercase", () => {
  expect(formatEnum("uses_rlaplace_or_inverse_cdf_sampler"))
    .toBe("uses rlaplace or inverse cdf sampler");
});

test("SCREAMING_SNAKE → space-separated lowercase", () => {
  expect(formatEnum("USER_MARKED_DONE")).toBe("user marked done");
  expect(formatEnum("EXPLICIT_ASK")).toBe("explicit ask");
});

test("mixed AND/lower → space-separated lowercase", () => {
  expect(formatEnum("plots_histogram_AND_theoretical_pdf_overlay"))
    .toBe("plots histogram and theoretical pdf overlay");
});

test("preserve list keeps tokens cased as supplied (case-insensitive match)", () => {
  expect(formatEnum("uses_rlaplace_or_inverse_cdf_sampler", { preserve: ["CDF", "PDF", "R"] }))
    .toBe("uses rlaplace or inverse CDF sampler");
  expect(formatEnum("plots_histogram_AND_theoretical_pdf_overlay", { preserve: ["PDF", "AND"] }))
    .toBe("plots histogram AND theoretical PDF overlay");
});

test("null returns empty string (defensive)", () => {
  expect(formatEnum(null)).toBe("");
});

test("undefined returns empty string (defensive)", () => {
  expect(formatEnum(undefined)).toBe("");
});

test("empty string returns empty string (defensive)", () => {
  expect(formatEnum("")).toBe("");
});

test("already-lowercase no-underscore input unchanged", () => {
  expect(formatEnum("open")).toBe("open");
  expect(formatEnum("hello world")).toBe("hello world");
});

test("single word with no underscores lowercased", () => {
  expect(formatEnum("CONCEPT")).toBe("concept");
});
