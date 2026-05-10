---
name: OK fixture
description: verify passes; cmd echoes magic and matches the expected regex
type: project
verify:
  - cmd: 'echo MAGIC_OK'
    expect_match: '^MAGIC_OK$'
    on_fail: 'this should never fire'
last_verified_at: '2026-05-10T00:00:00Z'
---

OK fixture body.
