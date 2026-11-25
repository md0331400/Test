import { useCallback, useEffect, useRef, useState } from 'react';
import {
  collection, query, where, onSnapshot, addDoc, updateDoc, doc,
  serverTimestamp, getDocs
} from 'firebase/firestore';
import { auth, db } from '../firebase';
import { getAllMessages, putMessage, normalizeMessage } from '../lib/localDb';
import { flushAckQueue, queueAck, sendPush, uploadImage } from '../lib/api';

async function upsert(list, msg) {
  const map = new Map(list.map(m => [m.id, m]));
  map.set(msg.id, msg);
  return Array.from(map.values()).sort((a, b) => a.ts - b.ts);
}

export default function useMessages(uid) {
  const [messages, setMessages] = useState([]);
  const [loading, setLoading] = useState(true);
  const uidRef = useRef(uid);
  uidRef.current = uid;

  useEffect(() => {
    if (!uid) return;
    let alive = true;
    setLoading(true);
    getAllMessages().then(rows => {
      if (!alive) return;
      const mine = rows.filter(m => m.senderId === uid || m.receiverId === uid).map(m => ({ ...m, ts: m.ts || Date.now() }));
      setMessages(mine.sort((a, b) => a.ts - b.ts));
      setLoading(false);
    }).catch(() => setLoading(false));

    const handle = async (raw, id) => {
      const current = uidRef.current;
      if (!current) return;
      const msg = normalizeMessage(raw, id);
      if (msg.senderId !== current && msg.receiverId !== current) return;
      if (Array.isArray(raw.deletedFor) && raw.deletedFor.includes(current)) return;
      await putMessage(msg).catch(() => {});
      setMessages(list => list.some(m => m.id === msg.id) ? list : upsert(list, msg));

      if (msg.receiverId === current && msg.senderId !== current) {
        // Device copy exists first; only then may the server copy be deleted.
        queueAck(id);
        if (!raw.delivered) updateDoc(doc(db, 'messages', id), { delivered: true }).catch(() => {});
        flushAckQueue().catch(() => {});
      }
    };

    const qs = [
      query(collection(db, 'messages'), where('senderId', '==', uid)),
      query(collection(db, 'messages'), where('receiverId', '==', uid))
    ];
    const unsubs = qs.map(q => onSnapshot(q, snap => {
      snap.docs.forEach(d => { handle(d.data(), d.id).catch(() => {}); });
    }, err => console.warn('[web] messages listener', err)));

    flushAckQueue().catch(() => {});
    const onOnline = () => flushAckQueue().catch(() => {});
    window.addEventListener('online', onOnline);
    return () => { alive = false; unsubs.forEach(fn => fn()); window.removeEventListener('online', onOnline); };
  }, [uid]);

  const sendText = useCallback(async (receiverId, text, replyTo = null) => {
    const current = auth.currentUser;
    if (!current || !text.trim()) return;
    const payload = {
      senderId: current.uid, receiverId, text: text.trim(),
      timestamp: serverTimestamp(), clientCreatedAt: Date.now(),
      delivered: false, read: false, seen: false
    };
    if (replyTo) payload.replyTo = replyTo;
    const ref = await addDoc(collection(db, 'messages'), payload);
    const local = normalizeMessage({ ...payload, clientCreatedAt: payload.clientCreatedAt }, ref.id);
    await putMessage(local).catch(() => {});
    setMessages(list => upsert(list, local));
    sendPush(receiverId, {
      title: current.displayName || 'Kotha Bolbo',
      body: text.trim(),
      url: '/',
      messageType: 'text',
      messageId: ref.id
    });
  }, []);

  const sendImage = useCallback(async (receiverId, file, caption = '') => {
    const current = auth.currentUser;
    if (!current) return;
    const upload = await uploadImage(file);
    const payload = {
      senderId: current.uid, receiverId, text: caption, type: 'image',
      imageUrl: upload.url, imageFileId: upload.fileId,
      timestamp: serverTimestamp(), clientCreatedAt: Date.now(),
      delivered: false, read: false, seen: false
    };
    const ref = await addDoc(collection(db, 'messages'), payload);
    const local = normalizeMessage(payload, ref.id);
    await putMessage(local).catch(() => {});
    setMessages(list => upsert(list, local));
    sendPush(receiverId, {
      title: current.displayName || 'Kotha Bolbo',
      body: caption || '🖼 Sent you a photo', image: upload.url,
      url: '/', messageType: 'image', messageId: ref.id
    });
  }, []);

  // Existing server messages are mirrored into the local archive on demand.
  const refreshFromServer = useCallback(async () => {
    const current = auth.currentUser;
    if (!current) return;
    const [sent, received] = await Promise.all([
      getDocs(query(collection(db, 'messages'), where('senderId', '==', current.uid))),
      getDocs(query(collection(db, 'messages'), where('receiverId', '==', current.uid)))
    ]);
    for (const d of [...sent.docs, ...received.docs]) await handle(d.data(), d.id);
  }, []);

  return { messages, loading, sendText, sendImage, refreshFromServer };
}
