import { useEffect, useState } from 'react';
import { onAuthStateChanged } from 'firebase/auth';
import { auth } from './firebase';
import AuthScreen from './components/AuthScreen';
import ChatApp from './components/ChatApp';

export default function App() {
  const [user, setUser] = useState(undefined);
  useEffect(() => onAuthStateChanged(auth, setUser), []);
  if (user === undefined) return <div className="boot">Loading…</div>;
  return user ? <ChatApp key={user.uid} /> : <AuthScreen />;
}
