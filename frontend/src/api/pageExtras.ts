import { api } from './client'

export interface PageComment { id: string; content: string; createdAt: string; createdByUserId: string; createdByFullName: string | null; createdByEmail: string }
export interface Summary { summary: string; cached: boolean; trimmed: boolean; generatedAt: string }
export interface Answer { answer: string; sources: { n: number; id: string; title: string }[]; usedDocuments: number; trimmed: boolean; cached: boolean }

export const getComments = (nodeId: string) => api<PageComment[]>(`/api/nodes/${nodeId}/comments`)
export const addComment = (nodeId: string, content: string) => api<PageComment>(`/api/nodes/${nodeId}/comments`, { method: 'POST', body: JSON.stringify({ content }) })
export const deleteComment = (id: string) => api(`/api/comments/${id}`, { method: 'DELETE' })
export const summarize = (nodeId: string) => api<Summary>(`/api/nodes/${nodeId}/ai/summary`, { method: 'POST' })
export const ask = (spaceId: string, question: string) => api<Answer>(`/api/spaces/${spaceId}/ai/ask`, { method: 'POST', body: JSON.stringify({ question }) })
