/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        primary: {
          DEFAULT: '#8bc53f',  
          dark: '#6fa030',
          light: '#a3d65c',
        },
        teal: {
          DEFAULT: '#0d5c5c', 
          dark: '#093f3f',
        },
        status: {
          approved: '#16a34a',
          rejected: '#dc2626',
          pending: '#6b7280',
        },
      },
      borderRadius: {
        pill: '9999px',
      },
    },
  },
  plugins: [],
};