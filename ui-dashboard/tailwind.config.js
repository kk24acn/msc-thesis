module.exports = {
  content: [
    "./src/**/*.{html,js,jsx,ts,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        darcula: {
          bg: 'rgb(var(--darcula-bg) / <alpha-value>)',
          card: 'rgb(var(--darcula-card) / <alpha-value>)',
          header: 'rgb(var(--darcula-header) / <alpha-value>)',
          accent: 'rgb(var(--darcula-accent) / <alpha-value>)',
          cyan: 'rgb(var(--darcula-cyan) / <alpha-value>)',
          purple: 'rgb(var(--darcula-purple) / <alpha-value>)',
          success: 'rgb(var(--darcula-success) / <alpha-value>)',
          warning: 'rgb(var(--darcula-warning) / <alpha-value>)',
          text: 'rgb(var(--darcula-text) / <alpha-value>)',
          muted: 'rgb(var(--darcula-muted) / <alpha-value>)',
          border: 'rgb(var(--darcula-border) / <alpha-value>)',
          contrast: 'rgb(var(--darcula-contrast) / <alpha-value>)',
          overlay: 'rgb(var(--darcula-overlay) / <alpha-value>)',
          deep: 'rgb(var(--darcula-deep) / <alpha-value>)',
        }
      },
      fontFamily: {
        mono: ['JetBrains Mono', 'Fira Code', 'monospace'],
        sans: ['Inter', 'system-ui', 'sans-serif']
      }
    }
  },
  plugins: [],
}
