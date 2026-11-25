import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// En desarrollo la API (Spring Boot) corre en el puerto 8090; en producción el jar sirve esta aplicación.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: { '/api': { target: 'http://localhost:8090', changeOrigin: true } },
  },
  build: {
    outDir: 'dist',
    chunkSizeWarningLimit: 1600,
    rollupOptions: {
      output: {
        manualChunks: { three: ['three'], motion: ['framer-motion'], mapa: ['d3-geo', 'topojson-client'] },
      },
    },
  },
});
