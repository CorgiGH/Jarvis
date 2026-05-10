---
name: Error fixture
description: verify cmd does not exist; runner must mark as ERROR not STALE
type: project
verify:
  - cmd: 'this-command-never-exists-anywhere'
    expect_exit: 0
    on_fail: 'binary should be installed'
---

Error fixture body.
