import { initializeApp } from 'firebase/app';
import { getAuth, GoogleAuthProvider } from 'firebase/auth';
import { getFirestore } from 'firebase/firestore';

// Public client config — the same values shipped in the Android app.
// Security comes from Firestore rules + App Check, not from hiding these.
const firebaseConfig = {
  apiKey: 'AIzaSyBa2bIHkBNK_oajNeFxgKpz4VrIx1aR5Fo',
  authDomain: 'kotha-bolbo-aso.firebaseapp.com',
  projectId: 'kotha-bolbo-aso',
  storageBucket: 'kotha-bolbo-aso.firebasestorage.app',
  messagingSenderId: '343828860232',
  appId: '1:343828860232:web:b566c74f630f8a2532b77d',
  databaseURL: 'https://kotha-bolbo-aso-default-rtdb.asia-southeast1.firebasedatabase.app'
};

export const app = initializeApp(firebaseConfig);
export const auth = getAuth(app);
export const db = getFirestore(app);
export const googleProvider = new GoogleAuthProvider();
googleProvider.setCustomParameters({ prompt: 'select_account' });
