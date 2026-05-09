// Layer B1 v1 effector: keystroke injection via `enigo`.
//
// Council R3 / spec §4: this is the LITERAL "type into the user's
// active editor window" path. v0 (clipboard write) lives server-side
// because it's safe / universal; v1 lives here because:
//   1. typing requires OS-level uinput / SendInput — only practical
//      from a native binary
//   2. the daemon is the single mutating endpoint per First-Principles
//      insight ("only process that can write to the user's editor")
//
// Future expansions (v2+): structured TextEdit application instead of
// raw key sequences (LSP applyEdit semantic), modifier-aware shortcuts
// (Ctrl+S to save), per-language keymap.

use enigo::{Enigo, Keyboard, Settings};

pub fn inject_keystrokes(text: &str) -> Result<(), String> {
    let mut enigo = Enigo::new(&Settings::default()).map_err(|e| format!("enigo init: {e:?}"))?;
    // text() handles Unicode + newlines + tabs cross-platform.
    enigo.text(text).map_err(|e| format!("enigo type: {e:?}"))?;
    Ok(())
}

#[cfg(test)]
mod tests {
    // Real keystroke injection requires a display server / focused
    // window so we cannot meaningfully unit-test in CI. The host-side
    // smoke test lives at scripts/smoke-daemon.ps1; run manually with
    // a known foreground app, then visually confirm the typed text.
}
