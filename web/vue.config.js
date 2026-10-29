const { defineConfig } = require('@vue/cli-service')

module.exports = defineConfig({
  transpileDependencies: true,
  lintOnSave: 'default',
  devServer: {
    host: '127.0.0.1',
    port: 8084,
    allowedHosts: 'all',
    client: {
      webSocketURL: 'auto://127.0.0.1:8084/ws',
      overlay: {
        errors: true,
        warnings: false,
        runtimeErrors: (error) => {
          if (error.message && error.message.includes('ResizeObserver')) return false
          return true
        }
      }
    },
    proxy: {
      '/ZhiYanAcademy': {
        target: 'http://localhost:8083',
        changeOrigin: true,
        ws: true,
        timeout: 600000,
        proxyTimeout: 600000
      },
      '/api-iconify': {
        target: 'https://api.iconify.design',
        changeOrigin: true,
        pathRewrite: { '^/api-iconify': '' },
        secure: true
      }
    }
  },
  productionSourceMap: false
})
