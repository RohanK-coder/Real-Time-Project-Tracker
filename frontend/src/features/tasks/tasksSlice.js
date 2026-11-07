import { createAsyncThunk, createSlice } from '@reduxjs/toolkit'
import api, { errorMessage } from '../../lib/api'

/*
 * Consistency model on the client
 * -------------------------------
 * Every server change carries the board's event `seq` (contiguous per board) and each task carries
 * `lastEventSeq` (the seq of its latest change).
 *
 *  - Applying a task is idempotent and order-safe: an incoming copy only replaces the stored one if
 *    its lastEventSeq is newer. Deleted tasks leave a tombstone seq so a late, older event can't
 *    resurrect them.
 *  - `lastSeq` is the highest seq up to which we have *everything*. Events can arrive slightly out of
 *    order (two requests that commit back-to-back publish independently), so seqs above lastSeq+1 are
 *    parked in `pendingSeqs` and drained in order as the missing ones arrive. Only if a hole stays
 *    open for GAP_GRACE_MS does the UI call catchUp(), which fetches /changes?sinceSeq=lastSeq.
 *  - catchUp() also runs after every (re)subscribe, which covers disconnects of any length.
 *  - Writes send the `version` the user edited. A 409 response stores a `conflict` with both copies,
 *    and the UI asks the user to keep theirs or re-apply mine. Nothing is silently overwritten.
 */

const initialState = {
  boardId: null,
  byId: {},
  tombstones: {}, // id -> seq at deletion
  lastSeq: 0,
  pendingSeqs: [], // received seqs above lastSeq+1 (out-of-order arrivals)
  needsSync: false, // true while a hole exists in the seq stream
  syncing: false,
  loading: false,
  hasNext: false,
  page: 0,
  error: null,
  conflict: null, // { mine, current, kind }
  // In-progress edit forms, keyed by task id. Kept in the store (not component state) so a draft
  // survives the card being re-rendered in another column when someone else moves it.
  drafts: {},
  search: { q: '', status: '', assignee: '', active: false, ids: [], page: 0, hasNext: false, total: 0, loading: false },
}

const PAGE = 200
export const GAP_GRACE_MS = 750

function drain(state) {
  state.pendingSeqs = state.pendingSeqs.filter((s) => s > state.lastSeq).sort((a, b) => a - b)
  while (state.pendingSeqs[0] === state.lastSeq + 1) {
    state.lastSeq = state.pendingSeqs.shift()
  }
  state.needsSync = state.pendingSeqs.length > 0
}

export const loadBoardTasks = createAsyncThunk('tasks/load', async ({ boardId, page = 0 }, { rejectWithValue }) => {
  try {
    // Board seq first: anything after it will arrive via WebSocket or the catch-up call.
    const seq = page === 0 ? (await api.get(`/boards/${boardId}`)).data.eventSeq : null
    const { data } = await api.get(`/boards/${boardId}/tasks`, { params: { page, size: PAGE, sort: 'updated' } })
    return { ...data, seq }
  } catch (error) {
    return rejectWithValue(errorMessage(error, 'Could not load tasks'))
  }
})

export const catchUp = createAsyncThunk('tasks/catchUp', async (_, { getState, rejectWithValue }) => {
  const { boardId, lastSeq } = getState().tasks
  try {
    return (await api.get(`/boards/${boardId}/changes`, { params: { sinceSeq: lastSeq } })).data
  } catch (error) {
    return rejectWithValue(errorMessage(error, 'Could not sync'))
  }
}, { condition: (_, { getState }) => !!getState().tasks.boardId && !getState().tasks.syncing })

export const searchTasks = createAsyncThunk('tasks/search', async ({ page = 0 } = {}, { getState, rejectWithValue }) => {
  const { boardId, search } = getState().tasks
  try {
    const params = { page, size: 50, sort: 'updated' }
    if (search.q.trim()) params.q = search.q.trim()
    if (search.status) params.status = search.status
    if (search.assignee) params.assignee = search.assignee
    return (await api.get(`/boards/${boardId}/tasks`, { params })).data
  } catch (error) {
    return rejectWithValue(errorMessage(error, 'Search failed'))
  }
})

const mutation = (name, request) => createAsyncThunk(`tasks/${name}`, async (arg, { getState, rejectWithValue }) => {
  const boardId = getState().tasks.boardId
  try {
    return await request(boardId, arg)
  } catch (error) {
    if (error.response?.status === 409 && error.response.data?.current) {
      return rejectWithValue({ conflict: { kind: name, mine: arg, current: error.response.data.current } })
    }
    return rejectWithValue({ message: errorMessage(error, `Could not ${name} task`) })
  }
})

export const createTask = mutation('create', async (b, payload) => (await api.post(`/boards/${b}/tasks`, payload)).data)
export const updateTask = mutation('update', async (b, { id, ...payload }) => (await api.put(`/boards/${b}/tasks/${id}`, payload)).data)
export const moveTask = mutation('move', async (b, { id, status, version }) => (await api.put(`/boards/${b}/tasks/${id}/move`, { status, version })).data)
export const deleteTask = mutation('delete', async (b, { id, version }) => {
  await api.delete(`/boards/${b}/tasks/${id}`, { params: { version } })
  return { id }
})

function applyTask(state, task) {
  const tomb = state.tombstones[task.id]
  if (tomb !== undefined && tomb >= task.lastEventSeq) return
  if (task.deleted) {
    delete state.byId[task.id]
    state.tombstones[task.id] = task.lastEventSeq
    return
  }
  const existing = state.byId[task.id]
  if (!existing || existing.lastEventSeq <= task.lastEventSeq) state.byId[task.id] = task
}

const slice = createSlice({
  name: 'tasks',
  initialState,
  reducers: {
    resetBoard: (_, { payload }) => ({ ...initialState, boardId: payload }),
    receiveEvent: (state, { payload: ev }) => {
      if (ev.boardId !== state.boardId) return
      if (ev.task) applyTask(state, ev.task) // safe in any order thanks to the per-task guard
      if (ev.seq <= state.lastSeq || state.pendingSeqs.includes(ev.seq)) return // duplicate
      state.pendingSeqs.push(ev.seq)
      drain(state)
      // During the initial load the post-load catch-up covers any hole.
      if (state.loading) state.needsSync = false
    },
    setSearch: (state, { payload }) => {
      Object.assign(state.search, payload)
      state.search.active = !!(state.search.q.trim() || state.search.status || state.search.assignee)
      if (!state.search.active) Object.assign(state.search, { ids: [], total: 0, hasNext: false, page: 0 })
    },
    dismissConflict: (state) => { state.conflict = null },
    setDraft: (state, { payload: { id, draft } }) => {
      if (draft) state.drafts[id] = { ...state.drafts[id], ...draft }
      else delete state.drafts[id]
    },
    clearTaskError: (state) => { state.error = null },
  },
  extraReducers: (b) => {
    b.addCase(loadBoardTasks.pending, (s) => { s.loading = true; s.error = null })
      .addCase(loadBoardTasks.fulfilled, (s, { payload, meta }) => {
        if (meta.arg.boardId !== s.boardId) return
        s.loading = false
        if (payload.seq !== null) s.lastSeq = Math.max(s.lastSeq, payload.seq)
        payload.items.forEach((t) => applyTask(s, t))
        s.page = payload.page
        s.hasNext = payload.hasNext
      })
      .addCase(loadBoardTasks.rejected, (s, a) => { s.loading = false; s.error = a.payload })
      .addCase(catchUp.pending, (s) => { s.syncing = true })
      .addCase(catchUp.fulfilled, (s, { payload }) => {
        s.syncing = false
        payload.tasks.forEach((t) => applyTask(s, t))
        // Events that arrived during the request may already be past currentSeq.
        s.lastSeq = Math.max(s.lastSeq, payload.currentSeq)
        drain(s)
      })
      .addCase(catchUp.rejected, (s) => { s.syncing = false })
      .addCase(searchTasks.pending, (s) => { s.search.loading = true })
      .addCase(searchTasks.fulfilled, (s, { payload }) => {
        s.search.loading = false
        payload.items.forEach((t) => applyTask(s, t))
        const ids = payload.items.map((t) => t.id)
        s.search.ids = payload.page === 0 ? ids : [...s.search.ids, ...ids]
        s.search.page = payload.page
        s.search.hasNext = payload.hasNext
        s.search.total = payload.totalItems
      })
      .addCase(searchTasks.rejected, (s, a) => { s.search.loading = false; s.error = a.payload })
      .addCase(deleteTask.fulfilled, (s, { payload }) => {
        const t = s.byId[payload.id]
        if (t) { s.tombstones[payload.id] = t.lastEventSeq + 1; delete s.byId[payload.id] }
      })
    for (const t of [createTask, updateTask, moveTask]) {
      b.addCase(t.fulfilled, (s, { payload }) => { applyTask(s, payload) })
    }
    for (const t of [createTask, updateTask, moveTask, deleteTask]) {
      b.addCase(t.rejected, (s, { payload }) => {
        if (payload?.conflict) {
          applyTask(s, payload.conflict.current) // show the real current state immediately
          s.conflict = payload.conflict
        } else {
          s.error = payload?.message || 'Request failed'
        }
      })
    }
  },
})

/** Updates the search filters and queries the server only when a filter is active. */
export const applySearch = (patch) => (dispatch, getState) => {
  dispatch(slice.actions.setSearch(patch))
  if (getState().tasks.search.active) dispatch(searchTasks({ page: 0 }))
}

export const { resetBoard, receiveEvent, setSearch, dismissConflict, setDraft, clearTaskError } = slice.actions
export default slice.reducer
