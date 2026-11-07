import axios from 'axios'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || '/api',
  timeout: 15000,
})

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

let onUnauthorized = () => {}
export function setUnauthorizedHandler(fn) {
  onUnauthorized = fn
}

api.interceptors.response.use(
  (res) => res,
  (error) => {
    if (error.response?.status === 401 && !error.config.url.startsWith('/auth/')) onUnauthorized()
    return Promise.reject(error)
  },
)

/** Problem-details aware error message. */
export function errorMessage(error, fallback) {
  const data = error.response?.data
  if (data?.errors) return Object.entries(data.errors).map(([k, v]) => `${k}: ${v}`).join(', ')
  return data?.detail || data?.error || fallback
}

export default api
