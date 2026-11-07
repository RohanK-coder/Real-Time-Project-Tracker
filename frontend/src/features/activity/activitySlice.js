import { createAsyncThunk, createSlice } from '@reduxjs/toolkit'
import api from '../../lib/api'

export const fetchActivity = createAsyncThunk('activity/fetch', async ({ boardId, page = 0 }) =>
  (await api.get(`/boards/${boardId}/activity`, { params: { page, size: 15 } })).data)

const slice = createSlice({
  name: 'activity',
  initialState: { boardId: null, items: [], page: 0, hasNext: false, loading: false },
  reducers: {
    resetActivity: (_, { payload }) => ({ boardId: payload, items: [], page: 0, hasNext: false, loading: false }),
  },
  extraReducers: (b) => {
    b.addCase(fetchActivity.pending, (s) => { s.loading = true })
      .addCase(fetchActivity.fulfilled, (s, { payload, meta }) => {
        s.loading = false
        if (meta.arg.boardId !== s.boardId) return
        if (payload.page === 0) {
          s.items = payload.items
        } else {
          const seen = new Set(s.items.map((i) => i.id))
          s.items.push(...payload.items.filter((i) => !seen.has(i.id)))
        }
        s.page = payload.page
        s.hasNext = payload.hasNext
      })
      .addCase(fetchActivity.rejected, (s) => { s.loading = false })
  },
})

export const { resetActivity } = slice.actions
export default slice.reducer
