import axios from 'axios'
import router from '../router'

const request = axios.create({
  withCredentials: true,
  timeout: 10000
})

request.interceptors.response.use(
  (res) => res.data,
  (err) => {
    const status = err.response?.status
    const msg = err.response?.data?.message || '请求失败'

    // 未登录时跳转登录页
    if (status === 400 && msg === '请先登录') {
      router.push('/login')
    }

    return Promise.reject(new Error(msg))
  }
)

export default request
