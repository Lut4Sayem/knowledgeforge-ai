import { api } from './client'

export interface DashTeam { teamId: string; name: string; description: string | null; role: string; spaceCount: number; documentCount: number }
export interface DashPage { documentId: string; title: string; status: string; updatedAt: string; teamName: string; spaceName: string }
export interface DashActivity { id: string; teamName: string; action: string; targetTitle: string | null; createdAt: string; actorFullName: string | null; actorEmail: string | null }
export interface Dashboard { teamCount: number; spaceCount: number; documentCount: number; teams: DashTeam[]; recentDocuments: DashPage[]; recentActivity: DashActivity[] }
export interface Member { userId: string; fullName: string; email: string; role: string }
export interface TeamDetail { team: { id: string; name: string; description: string | null }; teamMembers: Member[] }
export interface JoinRequest { id: string; fullName: string; email: string; status: string; requestedAt: string }
export interface TeamSpace { id: string; name: string; description: string | null }

const json = (method: string, body?: unknown): RequestInit => ({ method, body: body === undefined ? undefined : JSON.stringify(body) })

export const getDashboard = () => api<Dashboard>('/api/dashboard')
export const createTeam = (teamName: string, teamDescription: string) => api<{ id: string }>('/api/teams', json('POST', { teamName, teamDescription }))
export const getTeam = (id: string) => api<TeamDetail>(`/api/teams/${id}`)
export const getTeamSpaces = (id: string) => api<TeamSpace[]>(`/api/teams/${id}/spaces`)
export const createTeamSpace = (id: string, name: string, description: string) => api<TeamSpace>(`/api/teams/${id}/spaces`, json('POST', { name, description }))
export const setRole = (id: string, userId: string, role: string) => api(`/api/teams/${id}/members/${userId}/role`, json('PUT', { role }))
export const removeMember = (id: string, userId: string) => api(`/api/teams/${id}/members/${userId}`, json('DELETE'))
export const createInvite = (id: string) => api<{ inviteCode: string }>(`/api/teams/${id}/invite`, json('POST'))
export const getJoinRequests = (id: string) => api<JoinRequest[]>(`/api/teams/${id}/join-requests`)
export const answerJoinRequest = (id: string, rid: string, accept: boolean) => api(`/api/teams/${id}/join-requests/${rid}/${accept ? 'accept' : 'reject'}`, json('POST'))
