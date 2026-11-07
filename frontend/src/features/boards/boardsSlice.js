import { createAsyncThunk, createSlice } from '@reduxjs/toolkit'
import api, { errorMessage } from '../../lib/api'

const wrap = (fn, fallback) => async (arg, { rejectWithValue }) => {
  try {
    return await fn(arg)
  } catch (error) {
    return rejectWithValue(errorMessage(error, fallback))
  }
}

export const fetchBoards = createAsyncThunk('boards/fetch', wrap(async ({ page = 0 } = {}) =>
  (await api.get('/boards', { params: { page, size: 20 } })).data, 'Could not load boards'))

export const createBoard = createAsyncThunk('boards/create', wrap(async (payload) =>
  (await api.post('/boards', payload)).data, 'Could not create board'))

export const fetchBoard = createAsyncThunk('boards/fetchOne', wrap(async (id) =>
  (await api.get(`/boards/${id}`)).data, 'Could not load board'))

export const addMember = createAsyncThunk('boards/addMember', wrap(async ({ boardId, username, role }) =>
  (await api.post(`/boards/${boardId}/members`, { username, role })).data, 'Could not add member'))

export const updateMember = createAsyncThunk('boards/updateMember', wrap(async ({ boardId, username, role }) =>
  (await api.put(`/boards/${boardId}/members/${username}`, { role })).data, 'Could not change role'))

export const removeMember = createAsyncThunk('boards/removeMember', wrap(async ({ boardId, username }) => {
  await api.delete(`/boards/${boardId}/members/${username}`)
  return (await api.get(`/boards/${boardId}`)).data
}, 'Could not remove member'))

const slice = createSlice({
  name: 'boards',
  initialState: { list: [], page: 0, hasNext: false, current: null, currentId: null, error: null, loading: false },
  reducers: {
    selectBoard: (state, { payload }) => {
      state.currentId = payload
      state.current = null
      state.error = null
    },
    clearBoardError: (state) => { state.error = null },
  },
  extraReducers: (b) => {
    b.addCase(fetchBoards.pending, (s) => { s.loading = true })
      .addCase(fetchBoards.fulfilled, (s, { payload, meta }) => {
        s.loading = false
        s.list = (meta.arg?.page ?? 0) === 0 ? payload.items : [...s.list, ...payload.items]
        s.page = payload.page
        s.hasNext = payload.hasNext
        if (!s.currentId && payload.items.length) s.currentId = payload.items[0].id
      })
      .addCase(fetchBoards.rejected, (s, a) => { s.loading = false; s.error = a.payload })
      .addCase(createBoard.fulfilled, (s, { payload }) => {
        s.list.unshift({ id: payload.id, name: payload.name, description: payload.description, myRole: payload.myRole })
        s.currentId = payload.id
        s.current = payload
      })
      .addCase(fetchBoard.fulfilled, (s, { payload }) => { if (payload.id === s.currentId) s.current = payload })
      .addCase(fetchBoard.rejected, (s, a) => { s.error = a.payload })
    for (const t of [addMember, updateMember, removeMember]) {
      b.addCase(t.fulfilled, (s, { payload }) => { s.current = payload; s.error = null })
        .addCase(t.rejected, (s, a) => { s.error = a.payload })
    }
  },
})

export const { selectBoard, clearBoardError } = slice.actions
export default slice.reducer
