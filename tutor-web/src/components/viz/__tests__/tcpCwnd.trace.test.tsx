// 0.E.3 — TcpCwnd: actually run FAST_RECOVERY
// Builder: buildFrames() — exported as FRAMES

import { describe, it, expect } from "vitest";
import { FRAMES, FRAME_COUNT } from "../TcpCwnd";

describe("TcpCwnd — FAST_RECOVERY phase (0.E.3)", () => {
  it("exports a non-empty FRAMES array", () => {
    expect(FRAMES.length).toBeGreaterThan(0);
    expect(FRAMES.length).toBe(FRAME_COUNT);
  });

  it("loss frame has mode === 'FAST_RECOVERY'", () => {
    // Find the first frame whose event matches /PACKET LOSS/i
    const lossFrame = FRAMES.find(
      (f) => f.state.message && /PACKET LOSS/i.test(f.state.message)
    );
    expect(lossFrame, "no PACKET LOSS frame found").toBeDefined();
    expect(lossFrame!.state.mode).toBe("FAST_RECOVERY");
  });

  it("a frame after the loss frame transitions to CONG_AVOIDANCE", () => {
    const lossIdx = FRAMES.findIndex(
      (f) => f.state.message && /PACKET LOSS/i.test(f.state.message)
    );
    expect(lossIdx).toBeGreaterThan(-1);

    const laterFrames = FRAMES.slice(lossIdx + 1);
    const congAvoidFrame = laterFrames.find(
      (f) => f.state.mode === "CONG_AVOIDANCE"
    );
    expect(
      congAvoidFrame,
      "no CONG_AVOIDANCE frame found after FAST_RECOVERY"
    ).toBeDefined();
  });

  it("cwnd inflates (cwnd > ssthresh) during FAST_RECOVERY", () => {
    // During FAST_RECOVERY, cwnd should be inflated (ssthresh + 3 on entry
    // and grows by 1 per RTT while in recovery).
    const recoveryFrames = FRAMES.filter(
      (f) => f.state.mode === "FAST_RECOVERY"
    );
    expect(recoveryFrames.length).toBeGreaterThan(0);

    // On entry: cwnd = ssthresh + 3, so cwnd > ssthresh always
    for (const f of recoveryFrames) {
      expect(
        f.state.cwnd,
        `FAST_RECOVERY frame at rtt=${f.state.rtt} has cwnd ${f.state.cwnd} ≤ ssthresh ${f.state.ssthresh}`
      ).toBeGreaterThan(f.state.ssthresh);
    }
  });
});
