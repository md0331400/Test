import { useState } from 'react';
import {
  createUserWithEmailAndPassword,
  signInWithEmailAndPassword,
  signInWithPopup,
  updateProfile
} from 'firebase/auth';
import { doc, setDoc, serverTimestamp } from 'firebase/firestore';
import { auth, db, googleProvider } from '../firebase';

async function ensureUserProfile(user, extra = {}) {
  const ref = doc(db, 'users', user.uid);
  await setDoc(ref, {
    uid: user.uid,
    displayName: user.displayName || extra.displayName || user.email?.split('@')[0] || 'User',
    email: user.email || '',
    photoURL: user.photoURL || '',
    updatedAt: serverTimestamp()
  }, { merge: true });
}

export default function AuthScreen() {
  const [mode, setMode] = useState('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [name, setName] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit(e) {
    e.preventDefault();
    setError(''); setBusy(true);
    try {
      const cred = mode === 'signup'
        ? await createUserWithEmailAndPassword(auth, email.trim(), password)
        : await signInWithEmailAndPassword(auth, email.trim(), password);
      if (mode === 'signup' && name.trim()) await updateProfile(cred.user, { displayName: name.trim() });
      await ensureUserProfile(cred.user, { displayName: name.trim() });
    } catch (err) {
      setError(err.message || 'Authentication failed');
    } finally { setBusy(false); }
  }

  async function google() {
    setError(''); setBusy(true);
    try {
      const cred = await signInWithPopup(auth, googleProvider);
      await ensureUserProfile(cred.user);
    } catch (err) { setError(err.message || 'Google sign-in failed'); }
    finally { setBusy(false); }
  }

  return (
    <main className="auth-wrap">
      <div className="auth-card">
        <div className="brand"><span className="brand-dot" /> Kotha Bolbo</div>
        <p className="muted">Chat that stays on your device.</p>
        <div className="tabs">
          <button className={mode === 'login' ? 'active' : ''} onClick={() => { setMode('login'); setError(''); }}>Log in</button>
          <button className={mode === 'signup' ? 'active' : ''} onClick={() => { setMode('signup'); setError(''); }}>Sign up</button>
        </div>
        <form onSubmit={submit}>
          {mode === 'signup' && (
            <label>Name<input value={name} onChange={e => setName(e.target.value)} placeholder="Your name" /></label>
          )}
          <label>Email<input type="email" value={email} onChange={e => setEmail(e.target.value)} placeholder="you@example.com" required /></label>
          <label>Password<input type="password" value={password} onChange={e => setPassword(e.target.value)} placeholder="At least 6 characters" minLength={6} required /></label>
          {error && <p className="error">{error}</p>}
          <button className="primary" disabled={busy}>{busy ? 'Please wait…' : mode === 'login' ? 'Log in' : 'Create account'}</button>
        </form>
        <div className="divider">or</div>
        <button className="ghost" onClick={google} disabled={busy}>Continue with Google</button>
        <p className="hint">The Android app gives you device media folders, notifications and Google Drive backup. This website is the lightweight companion.</p>
      </div>
    </main>
  );
}
