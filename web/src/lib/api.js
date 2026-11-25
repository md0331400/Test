import { auth } from '../firebase';
import { ACK_URL, PUSH_URL, IK_AUTH_ENDPOINT, IK_UPLOAD_URL, IK_PUBLIC_KEY } from '../constants';

const ACK_KEY = 'kb_web_delivery_ack_queue';

export function queueAck(id) {
  if (!id) return;
  let queue = [];
  try { queue = JSON.parse(localStorage.getItem(ACK_KEY) || '[]'); } catch (_) {}
  const key = String(id);
  if (!Array.isArray(queue) || queue.includes(key)) return;
  queue.push(key);
  localStorage.setItem(ACK_KEY, JSON.stringify(queue.slice(-500)));
}

export async function flushAckQueue() {
  const user = auth.currentUser;
  if (!user || !navigator.onLine) return;
  let queue = [];
  try { queue = JSON.parse(localStorage.getItem(ACK_KEY) || '[]'); } catch (_) {}
  if (!Array.isArray(queue) || !queue.length) return;
  const token = await user.getIdToken().catch(() => null);
  if (!token) return;
  const remaining = [];
  for (const id of queue) {
    try {
      const res = await fetch(ACK_URL, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
        body: JSON.stringify({ messageId: id })
      });
      if (!res.ok && res.status !== 404) throw new Error(`ack ${res.status}`);
    } catch (_) { remaining.push(id); }
  }
  localStorage.setItem(ACK_KEY, JSON.stringify(remaining.slice(-500)));
}

export async function sendPush(receiverId, payload) {
  const user = auth.currentUser;
  if (!user) return;
  try {
    const token = await user.getIdToken();
    await fetch(PUSH_URL, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body: JSON.stringify({ receiverId, ...payload })
    });
  } catch (_) { /* push is best-effort */ }
}

export async function uploadImage(file, folder = 'web_chat') {
  let authData = null;
  try {
    const idToken = await auth.currentUser?.getIdToken?.();
    const res = await fetch(`${IK_AUTH_ENDPOINT}?folder=${encodeURIComponent(folder)}`, {
      headers: idToken ? { Authorization: `Bearer ${idToken}` } : {}
    });
    if (res.ok) authData = await res.json();
  } catch (_) {}
  const form = new FormData();
  const safe = file.name.replace(/[^a-zA-Z0-9.]/g, '_');
  form.append('file', file);
  form.append('fileName', `${folder}_${Date.now()}_${safe}`);
  form.append('publicKey', IK_PUBLIC_KEY);
  form.append('folder', `/${folder}`);
  if (authData?.signature && authData?.token && authData?.expire) {
    form.append('signature', authData.signature);
    form.append('token', authData.token);
    form.append('expire', String(authData.expire));
  }
  const res = await fetch(IK_UPLOAD_URL, { method: 'POST', body: form });
  if (!res.ok) throw new Error(`Upload failed (${res.status})`);
  const data = await res.json();
  if (!data.url) throw new Error('No URL returned');
  return { url: data.url, fileId: data.fileId || data.file_id || '' };
}
