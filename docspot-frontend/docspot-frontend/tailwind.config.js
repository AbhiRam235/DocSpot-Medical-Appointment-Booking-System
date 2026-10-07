/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{js,jsx}"],
  theme: {
    extend: {
      colors: {
        paper: "#F5F6F3",
        ink: {
          DEFAULT: "#14231F",
          soft: "#3E4A46",
          faint: "#75827D",
        },
        border: "#D8DED9",
        primary: {
          DEFAULT: "#0E5C52",
          dark: "#0A443D",
          light: "#E4EEEC",
        },
        accent: {
          DEFAULT: "#D98E3A",
          light: "#F6E7D2",
        },
        danger: {
          DEFAULT: "#B23A3A",
          light: "#F5E1E1",
        },
        success: {
          DEFAULT: "#2F7A4F",
          light: "#E1EEE5",
        },
      },
      fontFamily: {
        sans: ["Public Sans", "system-ui", "sans-serif"],
        display: ["Fraunces", "serif"],
      },
      borderRadius: {
        DEFAULT: "8px",
      },
    },
  },
  plugins: [],
};
