import { useEffect, useState } from 'react';
import { doc, onSnapshot, setDoc } from 'firebase/firestore';
import { signOut } from 'firebase/auth';
import { auth, db } from '../firebase';

function formatTime(value) {
  if (!value) return 'Never';
  const ms = value?.toMillis?.() || (value?.seconds ? value.seconds * 1000 : Number(value));
  if (!ms || Number.isNaN(ms)) return 'Never';
  return new Date(ms).toLocaleString();
}

export default function SettingsPanel() {
  const user = auth.currentUser;
  const [meta, setMeta] = useState(null);
  const [prefs, setPrefs] = useState({ includeMedia: false });

  useEffect(() => {
    if (!user) return;
    const unsubMeta = onSnapshot(doc(db, `users/${user.uid}/backup/meta`), snap => setMeta(snap.exists() ? snap.data() : null), () => {});
    const unsubPrefs = onSnapshot(doc(db, `users/${user.uid}/backup/prefs`), snap => {
      setPrefs(snap.exists() ? { includeMedia: false, ...snap.data() } : { includeMedia: false });
    }, () => {});
    return () => { unsubMeta(); unsubPrefs(); };
  }, [user?.uid]);

  async function toggleMedia() {
    if (!user) return;
    const next = !prefs.includeMedia;
    setPrefs(p => ({ ...p, includeMedia: next }));
    try {
      await setDoc(doc(db, `users/${user.uid}/backup/prefs`), { includeMedia: next }, { merge: true });
    } catch (_) { setPrefs(p => ({ ...p, includeMedia: !next })); }
  }

  return (
    <section className="panel">
      <h2>Settings</h2>

      <div className="card profile-card">
        <div className="avatar">{(user?.displayName || 'U').slice(0, 1).toUpperCase()}</div>
        <div>
          <strong>{user?.displayName || 'User'}</strong>
          <div className="muted small">{user?.email}</div>
        </div>
      </div>

      <div className="card">
        <h3>Google Drive backup</h3>
        <div className="kv"><span>Last backup</span><strong>{formatTime(meta?.lastBackupAt)}</strong></div>
        <div className="kv"><span>Status</span><strong>{meta?.status || 'Not connected'}</strong></div>
        <div className="kv"><span>Messages</span><strong>{meta?.messageCount ?? '—'}</strong></div>
        {meta?.sizeBytes != null && (
          <div className="kv"><span>Size</span><strong>{(meta.sizeBytes / 1024 / 1024).toFixed(1)} MB</strong></div>
        )}
        <p className="hint">Connect Google Drive inside the Android app. This website only shows the backup status — the Drive token never comes here.</p>
        <button className="row-button" onClick={toggleMedia}>
          <span>Include media in backup</span>
          <span className={`switch ${prefs.includeMedia ? 'on' : ''}`}><i /></span>
        </button>
        <p className="hint small">When on, the app encrypts media blobs into the backup. Backups get larger.</p>
      </div>

      <button className="danger" onClick={() => signOut(auth)}>Log out</button>
      <p className="hint center">Website does not receive push notifications. Install the Android app for notifications and local media folders.</p>
    </section>
  );
}
