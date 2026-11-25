import { useMemo, useRef, useEffect, useState } from 'react';
import { auth } from '../firebase';
import useMessages from '../hooks/useMessages';
import useUser from '../hooks/useUser';
import SettingsPanel from './SettingsPanel';

function initials(name) { return (name || 'U').trim().slice(0, 1).toUpperCase(); }

function ChatRow({ chat, onOpen }) {
  const profile = useUser(chat.uid);
  return (
    <button className="chat-item" onClick={() => onOpen(chat.uid)}>
      <span className="avatar small">{initials(profile?.displayName)}</span>
      <span className="chat-copy">
        <strong>{profile?.displayName || chat.uid.slice(0, 8)}</strong>
        <small>{chat.last?.type === 'image' ? '🖼 Photo' : chat.last?.text || 'No messages'}</small>
      </span>
      <time>{chat.last ? new Date(chat.last.ts).toLocaleDateString() : ''}</time>
    </button>
  );
}

function ChatList({ chats, onOpen, onRefresh, loading }) {
  return (
    <section className="panel">
      <div className="section-head">
        <h2>Chats</h2>
        <button className="icon-button" onClick={onRefresh} title="Refresh from server">↻</button>
      </div>
      {loading && <p className="muted center">Loading local chats…</p>}
      {!loading && !chats.length && (
        <div className="empty">
          <span>💬</span>
          <h3>No chats yet</h3>
          <p>Messages from the Android app will appear here.</p>
        </div>
      )}
      <div className="chat-list">
        {chats.map(chat => <ChatRow key={chat.uid} chat={chat} onOpen={onOpen} />)}
      </div>
    </section>
  );
}

function Conversation({ uid, profile, messages, onBack, onSendText, onSendImage }) {
  const [text, setText] = useState('');
  const [sending, setSending] = useState(false);
  const fileRef = useRef(null);
  const endRef = useRef(null);

  useEffect(() => { endRef.current?.scrollIntoView({ behavior: 'smooth' }); }, [messages.length]);

  async function submit(e) {
    e.preventDefault();
    if (!text.trim() || sending) return;
    setSending(true);
    try { await onSendText(uid, text); setText(''); }
    catch (err) { window.alert(err.message || 'Send failed'); }
    finally { setSending(false); }
  }

  async function pick(e) {
    const file = e.target.files?.[0];
    if (!file) return;
    setSending(true);
    try { await onSendImage(uid, file, text.trim()); setText(''); }
    catch (err) { window.alert(err.message || 'Upload failed'); }
    finally { setSending(false); e.target.value = ''; }
  }

  return (
    <section className="conversation">
      <header className="conv-head">
        <button className="icon-button" onClick={onBack}>←</button>
        <span className="avatar small">{initials(profile?.displayName)}</span>
        <div><strong>{profile?.displayName || 'Chat'}</strong><div className="muted small">{profile?.email || ''}</div></div>
      </header>
      <div className="bubbles">
        {messages.map(m => (
          <div key={m.id} className={`bubble ${m.senderId === auth.currentUser?.uid ? 'mine' : 'theirs'}`}>
            {m.type === 'image' && m.imageUrl ? <img src={m.imageUrl} alt="attachment" /> : null}
            {m.text ? <p>{m.text}</p> : null}
            <time>{new Date(m.ts).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</time>
          </div>
        ))}
        <div ref={endRef} />
      </div>
      <form className="composer" onSubmit={submit}>
        <button type="button" className="icon-button" onClick={() => fileRef.current?.click()} title="Attach image">＋</button>
        <input ref={fileRef} type="file" accept="image/*" hidden onChange={pick} />
        <input value={text} onChange={e => setText(e.target.value)} placeholder="Type a message…" />
        <button className="primary small" disabled={sending}>Send</button>
      </form>
    </section>
  );
}

export default function ChatApp() {
  const user = auth.currentUser;
  const [tab, setTab] = useState('chats');
  const [activeUid, setActiveUid] = useState(null);
  const { messages, loading, sendText, sendImage, refreshFromServer } = useMessages(user?.uid);
  const activeProfile = useUser(activeUid);

  const chats = useMemo(() => {
    const map = new Map();
    for (const m of messages) {
      const other = m.senderId === user.uid ? m.receiverId : m.senderId;
      const prev = map.get(other);
      if (!prev || m.ts >= prev.last.ts) map.set(other, { uid: other, last: m, profile: null });
    }
    return Array.from(map.values()).sort((a, b) => b.last.ts - a.last.ts);
  }, [messages, user.uid]);

  const activeMessages = useMemo(
    () => messages.filter(m => (m.senderId === user.uid && m.receiverId === activeUid) || (m.senderId === activeUid && m.receiverId === user.uid)),
    [messages, user.uid, activeUid]
  );

  return (
    <div className="app-shell">
      <header className="topbar">
        <div className="brand"><span className="brand-dot" /> Kotha Bolbo</div>
        <nav>
          <button className={tab === 'chats' && !activeUid ? 'active' : ''} onClick={() => { setTab('chats'); setActiveUid(null); }}>Chats</button>
          <button className={tab === 'settings' ? 'active' : ''} onClick={() => { setTab('settings'); setActiveUid(null); }}>Settings</button>
        </nav>
      </header>
      <main className="content">
        {tab === 'settings' ? <SettingsPanel /> : activeUid
          ? <Conversation
              uid={activeUid}
              profile={activeProfile}
              messages={activeMessages}
              onBack={() => setActiveUid(null)}
              onSendText={sendText}
              onSendImage={sendImage}
            />
          : <ChatList chats={chats} onOpen={setActiveUid} onRefresh={refreshFromServer} loading={loading} />}
      </main>
    </div>
  );
}
