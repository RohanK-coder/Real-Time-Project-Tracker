import { createSlice } from '@reduxjs/toolkit'

const slice = createSlice({
  name: 'realtime',
  initialState: { status: 'offline', attempt: 0, accessDenied: null },
  reducers: {
    statusChanged: (state, { payload }) => {
      state.status = payload.status
      state.attempt = payload.attempt
    },
    accessDenied: (state, { payload }) => { state.accessDenied = payload },
    clearAccessDenied: (state) => { state.accessDenied = null },
  },
})

export const { statusChanged, accessDenied, clearAccessDenied } = slice.actions
export default slice.reducer
