// Bounded LRU-ish nonce cache. Same semantics as Kotlin
// EffectorValidator.NonceCache: insert appends, when capacity is
// exceeded the oldest entries are evicted. Lookups + inserts are
// thread-safe via a Mutex around an inner state struct (single-user
// scale; contention is negligible).

use std::collections::{HashSet, VecDeque};
use std::sync::Mutex;

pub struct NonceCache {
    inner: Mutex<Inner>,
    capacity: usize,
}

struct Inner {
    set: HashSet<String>,
    queue: VecDeque<String>,
}

impl NonceCache {
    pub fn new(capacity: usize) -> Self {
        Self {
            inner: Mutex::new(Inner { set: HashSet::with_capacity(capacity * 2), queue: VecDeque::new() }),
            capacity,
        }
    }

    pub fn seen(&self, nonce: &str) -> bool {
        self.inner.lock().unwrap().set.contains(nonce)
    }

    pub fn record(&self, nonce: &str) {
        let mut g = self.inner.lock().unwrap();
        if g.set.insert(nonce.to_string()) {
            g.queue.push_back(nonce.to_string());
            while g.set.len() > self.capacity {
                if let Some(removed) = g.queue.pop_front() {
                    g.set.remove(&removed);
                } else {
                    break;
                }
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn record_then_seen() {
        let n = NonceCache::new(8);
        assert!(!n.seen("a"));
        n.record("a");
        assert!(n.seen("a"));
    }

    #[test]
    fn evicts_oldest_past_capacity() {
        let n = NonceCache::new(2);
        n.record("a"); n.record("b"); n.record("c");
        assert!(!n.seen("a"), "oldest evicted");
        assert!(n.seen("b"));
        assert!(n.seen("c"));
    }

    #[test]
    fn duplicate_record_idempotent() {
        let n = NonceCache::new(2);
        n.record("a"); n.record("a"); n.record("a");
        assert!(n.seen("a"));
    }
}
