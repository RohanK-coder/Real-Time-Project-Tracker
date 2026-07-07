import { createAsyncThunk, createSlice } from '@reduxjs/toolkit'
import api from '../../lib/api'

const initialState = {
  items: [],
  loading: false,
  error: null,
}

export const fetchTasks = createAsyncThunk('tasks/fetch', async (_, thunkAPI) => {
  try {
    const { data } = await api.get('/tasks')
    return data
  } catch (error) {
    return thunkAPI.rejectWithValue(error.response?.data?.error || 'Could not load tasks')
  }
})

export const createTask = createAsyncThunk('tasks/create', async (payload, thunkAPI) => {
  try {
    const { data } = await api.post('/tasks', payload)
    return data
  } catch (error) {
    return thunkAPI.rejectWithValue(error.response?.data?.error || 'Could not create task')
  }
})

export const updateTask = createAsyncThunk('tasks/update', async ({ id, payload }, thunkAPI) => {
  try {
    const { data } = await api.put(`/tasks/${id}`, payload)
    return data
  } catch (error) {
    return thunkAPI.rejectWithValue(error.response?.data?.error || 'Could not update task')
  }
})

export const moveTask = createAsyncThunk('tasks/move', async ({ id, status }, thunkAPI) => {
  try {
    const { data } = await api.put(`/tasks/${id}/move`, { status })
    return data
  } catch (error) {
    return thunkAPI.rejectWithValue(error.response?.data?.error || 'Could not move task')
  }
})

export const deleteTask = createAsyncThunk('tasks/delete', async (id, thunkAPI) => {
  try {
    await api.delete(`/tasks/${id}`)
    return id
  } catch (error) {
    return thunkAPI.rejectWithValue(error.response?.data?.error || 'Could not delete task')
  }
})

const upsert = (items, task) => {
  const index = items.findIndex((item) => item.id === task.id)
  if (index >= 0) items[index] = task
  else items.unshift(task)
}

const slice = createSlice({
  name: 'tasks',
  initialState,
  reducers: {
    receiveTaskUpdate: (state, action) => { upsert(state.items, action.payload) },
    receiveTaskDelete: (state, action) => { state.items = state.items.filter((t) => t.id !== action.payload) },
  },
  extraReducers: (builder) => {
    builder
      .addCase(fetchTasks.pending, (state) => { state.loading = true; state.error = null })
      .addCase(fetchTasks.fulfilled, (state, action) => { state.loading = false; state.items = action.payload })
      .addCase(fetchTasks.rejected, (state, action) => { state.loading = false; state.error = action.payload })
      .addCase(createTask.fulfilled, (state, action) => { upsert(state.items, action.payload) })
      .addCase(updateTask.fulfilled, (state, action) => { upsert(state.items, action.payload) })
      .addCase(moveTask.fulfilled, (state, action) => { upsert(state.items, action.payload) })
      .addCase(deleteTask.fulfilled, (state, action) => { state.items = state.items.filter((t) => t.id !== action.payload) })
  },
})

export const { receiveTaskUpdate, receiveTaskDelete } = slice.actions
export default slice.reducer
