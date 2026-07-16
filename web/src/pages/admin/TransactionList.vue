<template>
  <div class="transaction-list-page">
    <h2>交易流水</h2>
    <p class="text-secondary" style="margin-bottom:16px">审计视角 — 所有交易记录，不可修改、不可删除</p>

    <div class="card">
      <div v-if="loading" class="empty-hint">加载中...</div>
      <table class="table" v-else-if="transactions.length > 0">
        <thead>
          <tr>
            <th>交易编号</th>
            <th>账户ID</th>
            <th>类型</th>
            <th>金额</th>
            <th>方向</th>
            <th>来源</th>
            <th>时间</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="tx in transactions" :key="tx.id">
            <td><code>{{ tx.transactionNo }}</code></td>
            <td>{{ tx.accountId }}</td>
            <td>
              <span class="badge" :class="badgeClass(tx.transactionType)">
                {{ typeLabel(tx.transactionType) }}
              </span>
            </td>
            <td :class="tx.direction === 'IN' ? 'amount-in' : 'amount-out'">
              {{ tx.direction === 'IN' ? '+' : '-' }}¥{{ Number(tx.amount).toFixed(2) }}
            </td>
            <td>
              <span class="badge" :class="tx.direction === 'IN' ? 'badge-green' : 'badge-red'">
                {{ tx.direction === 'IN' ? '收入' : '支出' }}
              </span>
            </td>
            <td>{{ tx.referenceType || '-' }}</td>
            <td>{{ formatTime(tx.createTime) }}</td>
          </tr>
        </tbody>
      </table>
      <div class="empty-hint" v-else>暂无交易记录</div>
    </div>
    <div class="mt-4 text-center">
      <button class="btn" @click="loadMore" v-if="hasMore" :disabled="loadingMore">
        {{ loadingMore ? '加载中...' : '加载更多' }}
      </button>
      <span class="text-secondary" v-else>共 {{ total }} 条记录</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { transactionApi, type Transaction } from '@/api/transaction'

const transactions = ref<Transaction[]>([])
const loading = ref(true)
const loadingMore = ref(false)
const page = ref(1)
const total = ref(0)
const hasMore = ref(false)

function formatTime(t: string) {
  if (!t) return '-'
  return t.replace('T', ' ').substring(0, 19)
}
function typeLabel(t: string) {
  const map: Record<string, string> = { TOP_UP: '充值', CONSUME: '消费', REFUND: '退款', ADJUST: '调整' }
  return map[t] || t
}
function badgeClass(t: string) {
  const map: Record<string, string> = {
    TOP_UP: 'badge-green', CONSUME: 'badge-red', REFUND: 'badge-blue', ADJUST: 'badge-gold',
  }
  return map[t] || ''
}

// 通过查询 accountId=1 的交易列表作为全局流水视图
// 实际应该有一个全局的流水查询接口，这里简化处理
async function load() {
  loading.value = true
  try {
    const res = await transactionApi.listByAccount(1, page.value, 30)
    transactions.value = res.data.items
    total.value = res.data.total
    hasMore.value = res.data.hasNext
  } finally { loading.value = false }
}

async function loadMore() {
  loadingMore.value = true
  page.value++
  try {
    const res = await transactionApi.listByAccount(1, page.value, 30)
    transactions.value.push(...res.data.items)
    total.value = res.data.total
    hasMore.value = res.data.hasNext
  } finally { loadingMore.value = false }
}

onMounted(load)
</script>

<style scoped>
h2 { font-size: 17px; font-weight: 700; }
.empty-hint { text-align: center; color: var(--text-secondary); padding: 32px 0; font-size: 13px; }
code { font-size: 10px; background: var(--bg-secondary); padding: 2px 4px; border-radius: 3px; }
.amount-in { color: var(--green); font-weight: 500; }
.amount-out { color: var(--red); font-weight: 500; }
.badge-green { background: var(--green-bg); color: var(--green); }
.badge-red { background: var(--red-bg); color: var(--red); }
.badge-blue { background: var(--accent-bg); color: var(--accent); }
.badge-gold { background: var(--gold-bg); color: var(--gold); }
</style>