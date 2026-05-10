---
name: Stale fixture
description: verify cmd succeeds but output does not match expected regex
type: project
verify:
  - cmd: 'echo CURRENT_HASH'
    expect_match: '^OLD_HASH$'
    on_fail: 'hash drifted; rewrite the bundle line'
---

Stale fixture body.
