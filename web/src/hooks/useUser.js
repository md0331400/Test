import { useEffect, useState } from 'react';
import { doc, getDoc, onSnapshot } from 'firebase/firestore';
import { db } from '../firebase';

const cache = new Map();

export default function useUser(uid) {
  const [profile, setProfile] = useState(() => cache.get(uid) || null);
  useEffect(() => {
    if (!uid) { setProfile(null); return; }
    const ref = doc(db, 'users', uid);
    const unsub = onSnapshot(ref, snap => {
      const data = snap.exists() ? { ...snap.data(), uid } : { uid, displayName: 'User', photoURL: '' };
      cache.set(uid, data);
      setProfile(data);
    }, async () => {
      try {
        const snap = await getDoc(ref);
        const data = snap.exists() ? { ...snap.data(), uid } : { uid, displayName: 'User', photoURL: '' };
        cache.set(uid, data); setProfile(data);
      } catch (_) {}
    });
    return unsub;
  }, [uid]);
  return profile;
}
