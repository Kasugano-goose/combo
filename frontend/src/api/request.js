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
    // 防御：如果响应不是 JSON（例如代理错误返回了 HTML），抛出明确错误，
    // 避免下游把字符串当数组/对象用，导致 v-for 按字符渲染。
    if (typeof res.data === 'string' && res.data.trimStart().startsWith('<')) {
      console.error(`[响应错误] ${res.config.url}: 收到 HTML 而非 JSON，可能是代理配置错误`)
      return Promise.reject(new Error('接口返回了非 JSON 响应，请检查代理配置'))
    }
    return res.data
  },
  (err) => {
    const status = err.response?.status
    const msg = err.response?.data?.message || '请求失败'
    console.error(`[响应错误] ${err.config?.url} ${status}: ${msg}`)

    // 未登录时跳转登录页
    if (status === 401) {
      router.push('/login')
    }

    return Promise.reject(new Error(msg))
  }
)

export default request
