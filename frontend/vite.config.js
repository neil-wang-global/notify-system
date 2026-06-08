import vue from '@vitejs/plugin-vue';
import tailwindcss from '@tailwindcss/vite';
import { defineConfig } from 'vite';

export default defineConfig({
  plugins: [vue(), tailwindcss()],
  server: {
    proxy: {
      '/api': 'http://host.docker.internal:8080',
    },
  },
});
