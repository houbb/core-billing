<template>
  <div class="account-detail-page">
    <router-link to="/admin/accounts" class="back-link">← 返回账户列表</router-link>

    <div class="card mt-4" v-if="!loading">
      <div class="flex-between mb-4">
        <div>
          <h2>{{ account?.accountName }}</h2>
          <span class="text-secondary">{{ account?.accountType === 'PERSONAL' ? '个人账户' : '企业账户' }}</span>
        </div>
        <div class="text-right">
          <div class="text-secondary" style="font-size:11px">当前余额</div>
          <div class="balance-display">¥{{ formatBalance(balance) }}</div>
        </div>
      </div>
    </div>

    <div class="flex-between mt-4 mb-4">
      <h3>交易流水</h3>
      <button class="btn btn-primary" @click="showAdjust = true" v-if="isSuperAdmin">手工调整</button>
    </div>

    <!-- 手工调整弹窗 -->
    <div class="modal-overlay" v-if="showAdjust" @click.self="showAdjust = false">
      <div class="modal-card">
        <h4>手工调整余额</h4>
        <label class="label">金额（正数增加，负数扣减）</label>
        <input class="input" type="number" v-model="adjustAmount" step="0.01" />
        <label class="label mt-4">原因 <span style="color:var(--red)">*</span></label>
        <input class="input" v-model="adjustReason" placeholder="必填，如：测试补偿" />
        <div class="modal-actions mt-4">
          <button class="btn btn-primary" @click="doAdjust" :disabled="adjusting">确认调整</button>
          <button class="btn" @click="showAdjust = false">取消</button>
        </div>
      </div>
    </div>

    <div class="card" v-if="!txLoading">
      <table class="table" v-if="transactions.length > 0">
        <thead>
          <tr>
            <th>交易编号</th>
            <th>时间</th>
            <th>类型</th>
            <th>金额</th>
            <th>来源</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="tx in transactions" :key="tx.id">
            <td><code>{{ tx.transactionNo }}</code></td>
            <td>{{ formatTime(tx.createTime) }}</td>
            <td>
              <span class="badge" :class="typeBadgeClass(tx.transactionType)">
                {{ typeLabel(tx.transactionType) }}
              </span>
            </td>
            <td :class="tx.direction === 'IN' ? 'amount-in' : 'amount-out'">
              {{ tx.direction === 'IN' ? '+' : '-' }}¥{{ formatAmount(tx.amount) }}
            </td>
            <td>{{ tx.referenceType || '-' }}<br /><span class="text-secondary" style="font-size:10px">{{ tx.referenceId }}</span></td>
          </tr>
        </tbody>
      </table>
      <div class="empty-hint" v-else>暂无交易记录</div>
    </div>
    <div class="card" v-else><div class="empty-hint">加载中...</div></div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { accountApi, type Account } from '@/api/account'
import { transactionApi, type Transaction } from '@/api/transaction'

const route = useRoute()
const accountId = Number(route.params.id)

const account = ref<Account | null>(null)
const balance = ref(0)
const loading = ref(true)
const transactions = ref<Transaction[]>([])
const txLoading = ref(true)
const showAdjust = ref(false)
const adjustAmount = ref(0)
const adjustReason = ref('')
const adjusting = ref(false)
const isSuperAdmin = true // 本地开发默认超管

function formatBalance(v: number) { return v.toFixed(2) }
function formatAmount(v: number) { return Number(v).toFixed(2) }
function formatTime(t: string) {
  if (!t) return '-'
  return t.replace('T', ' ').substring(0, 19)
}
function typeLabel(t: string) {
  const map: Record<string, string> = { TOP_UP: '充值', CONSUME: '消费', REFUND: '退款', ADJUST: '调整' }
  return map[t] || t
}
function typeBadgeClass(t: string) {
  const map: Record<string, string> = {
    TOP_UP: 'badge-green', CONSUME: 'badge-red', REFUND: 'badge-blue', ADJUST: 'badge-gold',
  }
  return map[t] || ''
}

async function load() {
  loading.value = true
  txLoading.value = true
  try {
    const acc = await accountApi.get(accountId)
    account.value = acc.data
    const bal = await accountApi.getBalance(accountId)
    balance.value = bal.data.balance
    loading.value = false

    const txs = await transactionApi.listByAccount(accountId, 1, 50)
    transactions.value = txs.data.items
    txLoading.value = false
  } catch {
    loading.value = false
    txLoading.value = false
  }
}

async function doAdjust() {
  if (!adjustReason.value) return
  adjusting.value = true
  try {
    await transactionApi.adjustBalance(accountId, adjustAmount.value, adjustReason.value)
    showAdjust.value = false
    adjustReason.value = ''
    await load()
  } finally { adjusting.value = false }
}

onMounted(load)
</script>

<style scoped>
h2 { font-size: 17px; font-weight: 700; }
h3 { font-size: 15px; font-weight: 600; }
.back-link { font-size: 13px; }
.balance-display { font-size: 28px; font-weight: 300; margin-top: 4px; }
.amount-in { color: var(--green); font-weight: 500; }
.amount-out { color: var(--red); font-weight: 500; }
.empty-hint { text-align: center; color: var(--text-secondary); padding: 32px 0; font-size: 13px; }
code { font-size: 10px; background: var(--bg-secondary); padding: 2px 4px; border-radius: 3px; }

.badge-green { background: var(--green-bg); color: var(--green); }
.badge-red { background: var(--red-bg); color: var(--red); }
.badge-blue { background: var(--accent-bg); color: var(--accent); }
.badge-gold { background: var(--gold-bg); color: var(--gold); }

.modal-overlay {
  position: fixed; inset: 0;
  background: rgba(0,0,0,0.4);
  display: flex; align-items: center; justify-content: center;
  z-index: 100;
}
.modal-card {
  background: var(--bg-primary);
  border-radius: var(--radius);
  padding: 24px;
  width: 400px;
  box-shadow: 0 8px 40px rgba(0,0,0,0.15);
}
.modal-card h4 { margin-bottom: 16px; font-size: 15px; }
.modal-actions { display: flex; gap: 8px; }
</style>