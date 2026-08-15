import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import basicSsl from '@vitejs/plugin-basic-ssl'

// 폰에서 실제 GPS를 테스트하려면 HTTPS가 필요합니다.
// navigator.geolocation은 보안 컨텍스트에서만 동작하고, localhost만 예외입니다.
// 즉 http://192.168.x.x:5173 으로 접속하면 위치를 아예 받을 수 없습니다.
//
//   npm run dev         → http://localhost:5173      (PC 개발용)
//   npm run dev:https   → https://192.168.x.x:5173   (폰 GPS 테스트용)
//
// dev:https는 자체 서명 인증서를 쓰므로 폰에서 "안전하지 않음" 경고가 뜹니다.
// "고급 → 계속 진행"을 눌러야 들어가집니다.
const useHttps = process.env.VITE_HTTPS === 'true'

export default defineConfig({
  plugins: [react(), ...(useHttps ? [basicSsl()] : [])],
  base: './',
  server: {
    host: useHttps,        // HTTPS일 때만 외부 접속 허용(폰에서 접속)
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
