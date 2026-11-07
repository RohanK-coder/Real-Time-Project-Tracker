import { createAsyncThunk, createSlice } from '@reduxjs/toolkit'
import api, { errorMessage } from '../../lib/api'

const initialState = {
  token: localStorage.getItem('token'),
  username: localStorage.getItem('username'),
  role: localStorage.getItem('role'),
  loading: false,
  error: null,
}

export const login = createAsyncThunk('auth/login', async (payload, { rejectWithValue }) => {
  try {
    return (await api.post('/auth/login', payload)).data
  } catch (error) {
    return rejectWithValue(errorMessage(error, 'Login failed'))
  }
})

export const register = createAsyncThunk('auth/register', async (payload, { rejectWithValue }) => {
  try {
    return (await api.post('/auth/register', payload)).data
  } catch (error) {
    return rejectWithValue(errorMessage(error, 'Registration failed'))
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
      localStorage.removeItem('token')
      localStorage.removeItem('username')
      localStorage.removeItem('role')
    },
  },
  extraReducers: (builder) => {
    const fulfilled = (state, { payload }) => {
      Object.assign(state, { loading: false, error: null, token: payload.token, username: payload.username, role: payload.role })
      localStorage.setItem('token', payload.token)
      localStorage.setItem('username', payload.username)
      localStorage.setItem('role', payload.role)
    }
    const pending = (state) => { state.loading = true; state.error = null }
    const rejected = (state, action) => { state.loading = false; state.error = action.payload }
    builder
      .addCase(login.pending, pending).addCase(login.fulfilled, fulfilled).addCase(login.rejected, rejected)
      .addCase(register.pending, pending).addCase(register.fulfilled, fulfilled).addCase(register.rejected, rejected)
  },
})

export const { logout } = authSlice.actions
export default authSlice.reducer
