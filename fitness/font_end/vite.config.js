import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Chạy ở 127.0.0.1:5500 cho khớp app.fe.base-url của backend (CORS + trang kết quả VNPay).
// /api được chuyển tiếp sang backend nên trình duyệt chỉ thấy 1 origin, không vướng CORS.
export default defineConfig({
  plugins: [react()],
  server: {
    host: '127.0.0.1',
    port: 5500,
    strictPort: true,
    proxy: {
      '/api': {
        target: process.env.VITE_BACKEND_URL || 'http://localhost:8081',
        changeOrigin: true,
      },
    },
  },
});
