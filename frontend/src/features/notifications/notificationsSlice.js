import { createAsyncThunk, createSlice } from '@reduxjs/toolkit'
import api from '../../lib/api'

export const fetchNotifications = createAsyncThunk('notifications/fetch', async ({ page = 0 } = {}) => {
  const [list, count] = await Promise.all([
    api.get('/notifications', { params: { page, size: 15 } }),
    api.get('/notifications/unread-count'),
  ])
  return { ...list.data, unread: count.data.count }
})

export const markRead = createAsyncThunk('notifications/markRead', async (id) => (await api.post(`/notifications/${id}/read`)).data)
export const markAllRead = createAsyncThunk('notifications/markAllRead', async () => (await api.post('/notifications/read-all')).data)

const slice = createSlice({
  name: 'notifications',
  initialState: { items: [], unread: 0, page: 0, hasNext: false },
  reducers: {
    receiveNotification: (s, { payload }) => {
      if (s.items.some((n) => n.id === payload.id)) return
      s.items.unshift(payload)
      if (!payload.read) s.unread += 1
    },
  },
  extraReducers: (b) => {
    b.addCase(fetchNotifications.fulfilled, (s, { payload }) => {
      s.items = payload.page === 0 ? payload.items : [...s.items, ...payload.items]
      s.page = payload.page
      s.hasNext = payload.hasNext
      s.unread = payload.unread
    })
      .addCase(markRead.fulfilled, (s, { payload }) => {
        const n = s.items.find((i) => i.id === payload.id)
        if (n && !n.read) { n.read = true; s.unread = Math.max(0, s.unread - 1) }
      })
      .addCase(markAllRead.fulfilled, (s) => { s.items.forEach((n) => { n.read = true }); s.unread = 0 })
  },
})

export const { receiveNotification } = slice.actions
export default slice.reducer
