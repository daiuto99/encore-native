/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  darkMode: 'class',
  theme: {
    extend: {
      fontFamily: {
        sans: ['Inter', 'ui-sans-serif', 'system-ui', 'sans-serif'],
        mono: ['JetBrains Mono', 'ui-monospace', 'SFMono-Regular', 'monospace'],
      },
      boxShadow: {
        float: '0 12px 34px rgba(15, 23, 42, 0.24)',
        tablet: '0 8px 24px rgba(2, 6, 23, 0.32)',
      },
      borderRadius: {
        xl2: '12px',
      },
    },
  },
  plugins: [],
};
