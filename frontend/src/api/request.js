import axios from 'axios'
import router from '../router'

const request = axios.create({
  withCredentials: true,
  timeout: 10000
})

// 请求拦截器：打印每个请求
request.interceptors.request.use(
  (config) => {
    console.log(`[请求] ${config.method.toUpperCase()} ${config.url}`, config.params || '')
    return config
  },
  (err) => Promise.reject(err)
)

// 响应拦截器
request.interceptors.response.use(
  (res) => {
    console.log(`[响应] ${res.config.url} ${res.status}`, res.data)
    return res.data
  },
  (err) => {
    const status = err.response?.status
    const msg = err.response?.data?.message || '请求失败'
    console.error(`[响应错误] ${err.config?.url} ${status}: ${msg}`)

    // 未登录时跳转登录页
    if (status === 400 && msg === '请先登录') {
      router.push('/login')
    }

    return Promise.reject(new Error(msg))
  }
)

export default request
