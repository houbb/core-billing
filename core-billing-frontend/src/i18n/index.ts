const messages = {
  zh: {
    billingCenter: '商业中心',
    adminConsole: '商业平台',
    p1: 'P1 余额',
    p2: 'P2 定价',
    p3: 'P3 计量',
    p4: 'P4 配额',
    p5: 'P5 订阅',
    p6: 'P6 支付',
    p7: 'P7 账单',
    p8: 'P8 财务',
    p9: 'P9 企业',
    refresh: '刷新',
    execute: '执行',
    loading: '加载中...',
    empty: '暂无数据',
  },
  en: {
    billingCenter: 'Billing Center',
    adminConsole: 'Commerce Platform',
    p1: 'P1 Balance',
    p2: 'P2 Pricing',
    p3: 'P3 Metering',
    p4: 'P4 Quota',
    p5: 'P5 Subscription',
    p6: 'P6 Payment',
    p7: 'P7 Invoice',
    p8: 'P8 Finance',
    p9: 'P9 Enterprise',
    refresh: 'Refresh',
    execute: 'Execute',
    loading: 'Loading...',
    empty: 'No data',
  },
} as const

export type Locale = keyof typeof messages
export type MessageKey = keyof typeof messages.zh

let locale: Locale = 'zh'

export function setLocale(value: Locale) {
  locale = value
}

export function t(key: MessageKey) {
  return messages[locale][key]
}

