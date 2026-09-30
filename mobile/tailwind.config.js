/** @type {import('tailwindcss').Config} */
module.exports = {
  // Colours are taken from the SRM ECO TECH proposal decks so the app looks like what LHS already saw.
  content: ['./src/**/*.{ts,tsx}'],
  presets: [require('nativewind/preset')],
  theme: {
    extend: {
      colors: {
        ink: { DEFAULT: '#16202B', 2: '#34465A', 3: '#5E6D7D' },
        night: '#0E141B',
        line: '#DCE4EC',
        mist: '#F3F6F9',
        accent: { DEFAULT: '#E85D04', soft: '#FFEDE0', deep: '#B84A03' },
        teal: { DEFAULT: '#00A896', soft: '#DDF5F1', deep: '#007A6D' },
        danger: { DEFAULT: '#C62828', soft: '#FDECEA' },
        amber: { DEFAULT: '#B7791F', soft: '#FEF3C7' },
      },
      borderRadius: {
        card: '16px',
      },
    },
  },
  plugins: [],
};
