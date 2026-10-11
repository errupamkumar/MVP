/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        ink: { DEFAULT: '#111827', 2: '#374151', 3: '#6B7280', 4: '#9CA3AF' },
        line: '#E5E7EB',
        mist: '#F3F4F6',
        canvas: '#F7F8FA',
        brand: { DEFAULT: '#0A8F5B', dark: '#066B43', soft: '#E3F5EC' },
        // Chart series (validated categorical palette, fixed order) and status colours.
        series: { 1: '#2a78d6', 2: '#eb6834', 3: '#1baf7a' },
        good: '#0ca30c',
        warning: '#fab219',
        serious: '#ec835a',
        critical: '#d03b3b',
      },
      fontFamily: { sans: ['Inter', 'system-ui', '-apple-system', 'Segoe UI', 'Roboto', 'sans-serif'] },
    },
  },
  plugins: [],
};
