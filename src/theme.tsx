import * as SecureStore from 'expo-secure-store';
import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import { useColorScheme } from 'react-native';
// 크롬 확장 popup.css의 색과 theme.js의 규칙이다. 처음에는 기기 설정을 따르고, 직접 고르면 그 값을 저장한다.
// 기기의 밝은/어두운 모드가 바뀌면 고른 값을 지우고 기기 설정을 따른다.

const light = {
  dark: false,
  bg: '#fff',
  page: '#f3f5f9',
  fg: '#152746',
  muted: '#5d6b82',
  line: '#d5dbe5',
  accent: '#2f5bd3',
  danger: '#c0392b',
  // 기록 중 카드(웹앱 .timer-panel)
  active: '#4b5058',
  activeText: '#f7f8f9',
  activeMuted: '#d5d9de',
  toggleBg: '#fff',
  toggleText: '#24324d',
  toggleLine: '#d5dce8',
  activeOutline: '#858c95',
};
const dark: typeof light = {
  dark: true,
  bg: '#121a2a',
  page: '#0f1419',
  fg: '#e8edf6',
  muted: '#9aa7bd',
  line: '#2c3a55',
  accent: '#88adff',
  danger: '#ff8a7a',
  active: '#262b33',
  activeText: '#f7f8f9',
  activeMuted: '#d5d9de',
  toggleBg: '#212936',
  toggleText: '#f7f8f9',
  toggleLine: '#2c3a55',
  activeOutline: '#5d6570',
};
export type Colors = typeof light;

const KEY = 'theme';
const ThemeContext = createContext<{ c: Colors; toggle: () => void }>({ c: light, toggle: () => {} });
export const useTheme = () => useContext(ThemeContext);

export function ThemeProvider({ children }: { children: ReactNode }) {
  const system = useColorScheme() === 'dark' ? 'dark' : 'light';
  const [chosen, setChosen] = useState<'dark' | 'light' | null>(null);
  const [seenSystem, setSeenSystem] = useState(system);
  useEffect(() => {
    SecureStore.getItemAsync(KEY)
      .then((v) => (v === 'dark' || v === 'light') && setChosen(v))
      .catch(() => {});
  }, []);
  if (system !== seenSystem) {
    setSeenSystem(system);
    setChosen(null);
    SecureStore.deleteItemAsync(KEY).catch(() => {});
  }
  const mode = chosen ?? system;
  const toggle = () => {
    const next = mode === 'dark' ? 'light' : 'dark';
    setChosen(next);
    SecureStore.setItemAsync(KEY, next).catch(() => {});
  };
  return <ThemeContext.Provider value={{ c: mode === 'dark' ? dark : light, toggle }}>{children}</ThemeContext.Provider>;
}
