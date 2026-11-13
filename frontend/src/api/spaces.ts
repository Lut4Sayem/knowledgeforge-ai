import { api } from './client'

export interface MySpace {
  id: string
  name: string
  icon: string | null
  ownerType: 'TEAM' | 'USER'
  teamId: string | null
  teamName: string | null
  myRole: string | null
}

export interface MyTeam {
  teamId: string
  teamName: string
  role: string
}

export interface Me {
  id: string
  fullName: string
  email: string
  profilePicture?: string | null
}

export const getMySpaces = () => api<MySpace[]>('/api/me/spaces')
export const getMyTeams = () => api<MyTeam[]>('/api/me/teams')
export const getMe = () => api<Me>('/api/me')
