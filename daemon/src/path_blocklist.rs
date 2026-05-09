// Mirrors Kotlin EffectorValidator path blocklist. Daemon enforces
// it independently so a server compromise that minted a valid HMAC
// still can't write to ~/.ssh/.

pub fn denied(target_uri: &str) -> bool {
    // Strip file:// + windows-leading slash so the same regex literals
    // match cross-platform.
    let p = strip_file_uri(target_uri);
    let p_lower = p.replace('\\', "/");
    if DENIED_SUBSTRINGS.iter().any(|needle| p_lower.contains(needle)) {
        return true;
    }
    let leaf = p_lower.rsplit('/').next().unwrap_or(&p_lower);
    if DENIED_SUFFIXES.iter().any(|suf| leaf.ends_with(suf)) {
        return true;
    }
    // .env, .env.production, .env.local — any leaf starting with `.env`.
    if leaf.starts_with(".env") {
        return true;
    }
    false
}

fn strip_file_uri(uri: &str) -> String {
    let mut s = uri.strip_prefix("file://").unwrap_or(uri).to_string();
    if s.len() >= 4 && s.starts_with('/') && s.as_bytes()[2] == b':' {
        s.remove(0);
    }
    s
}

const DENIED_SUBSTRINGS: &[&str] = &[
    "/.ssh/", "/.git/", "/.aws/", "/.config/", "/.kube/",
];

const DENIED_SUFFIXES: &[&str] = &[
    ".env", ".key", ".pem",
];

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn denies_ssh_dir() {
        assert!(denied("file:///home/u/.ssh/id_rsa"));
    }
    #[test]
    fn denies_git_internals() {
        assert!(denied("file:///proj/.git/config"));
    }
    #[test]
    fn denies_env_file() {
        assert!(denied("file:///proj/.env"));
        assert!(denied("file:///proj/.env.production"));
    }
    #[test]
    fn denies_pem_key() {
        assert!(denied("file:///etc/ssl/site.pem"));
        assert!(denied("file:///home/u/cert.key"));
    }
    #[test]
    fn allows_normal_source_file() {
        assert!(!denied("file:///work/app/src/Foo.kt"));
        assert!(!denied("file:///c/users/u/proj/main.py"));
    }
    #[test]
    fn case_handles_windows_paths() {
        assert!(denied("file:///C:/Users/u/.ssh/config"));
    }
}
