// Layer B1 kill switch — a simple file-presence check that aborts
// every dispatch. The user (or a remote `/jarvis stop` Telegram cmd
// shipped in a future surface) writes ~/.jarvis/KILL to halt all
// effector activity without needing to terminate the daemon process.
//
// Defaults to ~/.jarvis/KILL but honors $JARVIS_KILL_FILE for tests.

use std::path::PathBuf;

pub fn active() -> bool {
    kill_path().map(|p| p.exists()).unwrap_or(false)
}

fn kill_path() -> Option<PathBuf> {
    if let Ok(s) = std::env::var("JARVIS_KILL_FILE") {
        return Some(PathBuf::from(s));
    }
    Some(dirs::home_dir()?.join(".jarvis").join("KILL"))
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::Mutex;

    // Env var mutation is process-global; serialize across tests.
    static LOCK: Mutex<()> = Mutex::new(());

    #[test]
    fn absent_when_file_missing() {
        let _g = LOCK.lock().unwrap();
        let dir = tempfile_dir();
        let path = dir.join("KILL");
        std::env::set_var("JARVIS_KILL_FILE", &path);
        let _ = std::fs::remove_file(&path);
        assert!(!active());
        std::env::remove_var("JARVIS_KILL_FILE");
    }

    #[test]
    fn active_when_file_present() {
        let _g = LOCK.lock().unwrap();
        let dir = tempfile_dir();
        let path = dir.join("KILL");
        std::fs::write(&path, b"x").unwrap();
        std::env::set_var("JARVIS_KILL_FILE", &path);
        assert!(active());
        std::fs::remove_file(&path).unwrap();
        std::env::remove_var("JARVIS_KILL_FILE");
    }

    fn tempfile_dir() -> PathBuf {
        let p = std::env::temp_dir().join(format!("jarvis-kill-{}", std::process::id()));
        std::fs::create_dir_all(&p).unwrap();
        p
    }
}
