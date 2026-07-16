import axios from 'axios'
import { config } from '@/app/config'

const http = axios.create({
  baseURL: config.apiBase,
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
})

http.interceptors.request.use((reqConfig) => {
  reqConfig.headers['X-User-Id'] = 'demo-user'
  reqConfig.headers['X-Tenant-Id'] = 'default'
  reqConfig.headers['X-Role'] = 'ADMIN'
  return reqConfig
})

http.interceptors.response.use(
  (response) => response,
  (error) => {
    const msg = error.response?.data?.detail || error.message || '请求失败'
    console.error('[API Error]', msg)
    return Promise.reject(error)
  }
)

export default http