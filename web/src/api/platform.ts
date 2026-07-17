import http from './http'

export type RuntimeRow = Record<string, unknown>

export const platformApi = {
  balances(accountId = 1) {
    return http.get<RuntimeRow[]>(`/accounts/${accountId}/balances`)
  },
  usage(period: string) {
    return http.get<RuntimeRow[]>('/usage/summary', { params: { period } })
  },
  quota(tenantId = 'default') {
    return http.get<RuntimeRow[]>(`/quota/${tenantId}`)
  },
  plans() {
    return http.get<RuntimeRow[]>('/plans')
  },
  subscribe(plan: string) {
    return http.post<RuntimeRow>('/subscriptions', { plan })
  },
  currentSubscription() {
    return http.get<RuntimeRow>('/subscription/current')
  },
  payments() {
    return http.get<RuntimeRow[]>('/payments/orders')
  },
  createTopUpOrder(accountId: number, amount: number) {
    return http.post<RuntimeRow>('/payments/orders', {
      businessType: 'TOP_UP',
      businessId: String(accountId),
      accountId,
      amount,
      channelCode: 'MOCK',
      idempotencyKey: `WEB_TOPUP_${Date.now()}`,
    })
  },
  invoices() {
    return http.get<RuntimeRow[]>('/invoices')
  },
  adminGet(path: string) {
    return http.get(path)
  },
  adminPost(path: string, body: RuntimeRow = {}) {
    return http.post(path, body)
  },
}

