import { createAsyncThunk, createSlice } from '@reduxjs/toolkit'
import api from '../../lib/api'

const initialState = {
  token: localStorage.getItem('token'),
  username: localStorage.getItem('username'),
  role: localStorage.getItem('role'),
  loading: false,
  error: null,
}

export const login = createAsyncThunk('auth/login', async (payload, thunkAPI) => {
  try {
    const { data } = await api.post('/auth/login', payload)
    return data
  } catch (error) {
    return thunkAPI.rejectWithValue(error.response?.data?.error || 'Login failed')
  }
})

export const register = createAsyncThunk('auth/register', async (payload, thunkAPI) => {
  try {
    const { data } = await api.post('/auth/register', payload)
    return data
  } catch (error) {
    return thunkAPI.rejectWithValue(error.response?.data?.error || 'Registration failed')
  }
})

const authSlice = createSlice({
  name: 'auth',
  initialState,
  reducers: {
    logout: (state) => {
      state.token = null
      state.username = null
      state.role = null
      localStorage.clear()
    },
  },
  extraReducers: (builder) => {
    const fulfilled = (state, action) => {
      state.loading = false
      state.error = null
      state.token = action.payload.token
      state.username = action.payload.username
      state.role = action.payload.role
      localStorage.setItem('token', action.payload.token)
      localStorage.setItem('username', action.payload.username)
      localStorage.setItem('role', action.payload.role)
    }
    builder
      .addCase(login.pending, (state) => { state.loading = true; state.error = null })
      .addCase(login.fulfilled, fulfilled)
      .addCase(login.rejected, (state, action) => { state.loading = false; state.error = action.payload })
      .addCase(register.pending, (state) => { state.loading = true; state.error = null })
      .addCase(register.fulfilled, fulfilled)
      .addCase(register.rejected, (state, action) => { state.loading = false; state.error = action.payload })
  },
})

export const { logout } = authSlice.actions
export default authSlice.reducer
