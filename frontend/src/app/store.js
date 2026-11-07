import { configureStore } from '@reduxjs/toolkit'
import authReducer from '../features/auth/authSlice'
import boardsReducer from '../features/boards/boardsSlice'
import tasksReducer from '../features/tasks/tasksSlice'
import activityReducer from '../features/activity/activitySlice'
import notificationsReducer from '../features/notifications/notificationsSlice'
import realtimeReducer from '../features/realtimeSlice'

export const store = configureStore({
  reducer: {
    auth: authReducer,
    boards: boardsReducer,
    tasks: tasksReducer,
    activity: activityReducer,
    notifications: notificationsReducer,
    realtime: realtimeReducer,
  },
})
