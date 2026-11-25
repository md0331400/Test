const DB_NAME = 'kothabolbo-web';
const STORE = 'messages';
const VERSION = 1;

function open() {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, VERSION);
    request.onupgradeneeded = () => {
      const db = request.result;
      if (!db.objectStoreNames.contains(STORE)) db.createObjectStore(STORE, { keyPath: 'id' });
    };
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

function tx(db, mode, fn) {
  return new Promise((resolve, reject) => {
    const t = db.transaction(STORE, mode);
    const store = t.objectStore(STORE);
    const request = fn(store);
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

export async function getAllMessages() {
  const db = await open();
  try { return (await tx(db, 'readonly', s => s.getAll())) || []; }
  finally { db.close(); }
}

export async function putMessage(msg) {
  const db = await open();
  try { return await tx(db, 'readwrite', s => s.put(msg)); }
  finally { db.close(); }
}

export async function deleteMessage(id) {
  const db = await open();
  try { return await tx(db, 'readwrite', s => s.delete(id)); }
  finally { db.close(); }
}

/** Convert a Firestore document into a plain, IndexedDB-safe object. */
export function normalizeMessage(raw, id) {
  const { timestamp, ...rest } = raw || {};
  const millis = timestamp?.toMillis?.()
    || (timestamp?.seconds ? timestamp.seconds * 1000 : 0)
    || rest.clientCreatedAt
    || Date.now();
  return { ...rest, id: String(id), ts: millis };
}
