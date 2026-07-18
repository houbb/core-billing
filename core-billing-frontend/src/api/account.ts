import http from './http'

export interface Account {
  id: number
  accountName: string
  accountType: string
  status: string
  createTime: string
}

export interface Balance {
  accountId: number
  balance: number
  currency: string
}

export const accountApi = {
  create(name: string, type: string) {
    return http.post<{ id: number }>('/accounts', { name, type })
  },
  get(id: number) {
    return http.get<Account>(`/accounts/${id}`)
  },
  getBalance(id: number) {
    return http.get<Balance>(`/accounts/${id}/balance`)
  },
  listAdmin(page = 1, size = 20) {
    return http.get('/admin/accounts', { params: { page, size } })
  },
}