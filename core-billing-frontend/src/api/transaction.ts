import http from './http'

export interface Transaction {
  id: number
  accountId: number
  transactionNo: string
  transactionType: string
  amount: number
  direction: string
  referenceType: string
  referenceId: string
  description: string
  createTime: string
}

export interface PagedResponse<T> {
  items: T[]
  page: number
  size: number
  total: number
  hasNext: boolean
}

export const transactionApi = {
  create(payload: {
    accountId: number
    type: string
    amount: number
    referenceType?: string
    referenceId?: string
    description?: string
  }) {
    return http.post<Transaction>('/transactions', payload)
  },
  listByAccount(accountId: number, page = 1, size = 20) {
    return http.get<PagedResponse<Transaction>>(`/transactions/account/${accountId}`, {
      params: { page, size },
    })
  },
  get(id: number) {
    return http.get<Transaction>(`/transactions/${id}`)
  },
  listAdmin(page = 1, size = 50) {
    return http.get<PagedResponse<Transaction>>('/admin/transactions', {
      params: { page, size },
    })
  },
  adjustBalance(accountId: number, amount: number, reason: string) {
    return http.post(`/admin/accounts/${accountId}/adjust`, { amount, reason })
  },
}
