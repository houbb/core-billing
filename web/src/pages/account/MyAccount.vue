<template>
  <div class="my-account">
    <div class="balance-hero">
      <div class="balance-label">账户余额</div>
      <div class="balance-amount" v-if="!loading">
        <span class="currency">¥</span>
        <span class="value">{{ formatBalance(balance) }}</span>
      </div>
      <div class="balance-amount skeleton" v-else>---</div>
      <div class="balance-meta" v-if="!loading">
        <span class="badge" :class="balance >= 0 ? 'badge-positive' : 'badge-overdue'">
          {{ balance >= 0 ? '正常' : '欠费' }}
        </span>
        <span class="meta-text">{{ currency }}</span>
      </div>
    </div>

    <div class="actions-row">
      <h3 class="section-title">快捷操作</h3>
      <div class="action-buttons">
        <button class="btn btn-emphasis" @click="showTopUp = true">模拟充值</button>
        <button class="btn" @click="showConsume = true">模拟消费</button>
        <button class="btn" @click="showRefund = true">模拟退款</button>
      </div>
    </div>

    <!-- 充值弹窗 -->
    <div class="modal-overlay" v-if="showTopUp" @click.self="showTopUp = false">
      <div class="modal-card">
        <h4>模拟充值</h4>
        <label class="label">金额</label>
        <input class="input" type="number" v-model="topUpAmount" min="0.01" step="0.01" />
        <div class="modal-actions mt-4">
          <button class="btn btn-primary" @click="doTopUp" :disabled="submitting">确认充值</button>
          <button class="btn" @click="showTopUp = false">取消</button>
        </div>
      </div>
    </div>

    <!-- 消费弹窗 -->
    <div class="modal-overlay" v-if="showConsume" @click.self="showConsume = false">
      <div class="modal-card">
        <h4>模拟消费</h4>
        <label class="label">金额</label>
        <input class="input" type="number" v-model="consumeAmount" min="0.01" step="0.01" />
        <label class="label mt-4">来源描述</label>
        <input class="input" v-model="consumeRef" placeholder="如：AI_REQUEST" />
        <div class="modal-actions mt-4">
          <button class="btn btn-danger" @click="doConsume" :disabled="submitting">确认消费</button>
          <button class="btn" @click="showConsume = false">取消</button>
        </div>
      </div>
    </div>

    <!-- 退款弹窗 -->
    <div class="modal-overlay" v-if="showRefund" @click.self="showRefund = false">
      <div class="modal-card">
        <h4>模拟退款</h4>
        <label class="label">金额</label>
        <input class="input" type="number" v-model="refundAmount" min="0.01" step="0.01" />
        <label class="label mt-4">退款原因</label>
        <input class="input" v-model="refundRef" placeholder="如：服务异常" />
        <div class="modal-actions mt-4">
          <button class="btn btn-primary" @click="doRefund" :disabled="submitting">确认退款</button>
          <button class="btn" @click="showRefund = false">取消</button>
        </div>
      </div>
    </div>

    <h3 class="section-title mt-4">最近消费</h3>
    <div class="card" v-if="!txLoading">
      <div class="empty-hint" v-if="transactions.length === 0">暂无交易记录</div>
      <table class="table" v-else>
        <thead>
          <tr>
            <th>时间</th>
            <th>类型</th>
            <th>金额</th>
            <th>来源</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="tx in transactions" :key="tx.id">
            <td>{{ formatTime(tx.createTime) }}</td>
            <td>
              <span class="badge" :class="typeBadgeClass(tx.transactionType)">
                {{ typeLabel(tx.transactionType) }}
              </span>
            </td>
            <td :class="tx.direction === 'IN' ? 'amount-in' : 'amount-out'">
              {{ tx.direction === 'IN' ? '+' : '-' }}¥{{ formatAmount(tx.amount) }}
            </td>
            <td>{{ tx.referenceType || '-' }}</td>
          </tr>
        </tbody>
      </table>
    </div>
    <div class="card" v-else><div class="empty-hint">加载中...</div></div>

    <div class="mt-4 flex-between">
      <span class="text-secondary">交易记录 · 共 {{ txTotal }} 条</span>
      <router-link to="/admin/accounts" class="btn btn-emphasis" style="font-size:12px">管理后台</router-link>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { accountApi } from '@/api/account'
import { transactionApi, type Transaction } from '@/api/transaction'

const ACCOUNT_ID = 1

const balance = ref(0)
const currency = ref('CNY')
const loading = ref(true)
const transactions = ref<Transaction[]>([])
const txTotal = ref(0)
const txLoading = ref(true)
const submitting = ref(false)

// Modal states
const showTopUp = ref(false)
const showConsume = ref(false)
const showRefund = ref(false)
const topUpAmount = ref(100)
const consumeAmount = ref(5)
const consumeRef = ref('AI_REQUEST')
const refundAmount = ref(5)
const refundRef = ref('')

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

async function loadData() {
  loading.value = true
  txLoading.value = true
  try {
    const bal = await accountApi.getBalance(ACCOUNT_ID)
    balance.value = bal.data.balance
    currency.value = bal.data.currency
    loading.value = false

    const txs = await transactionApi.listByAccount(ACCOUNT_ID, 1, 10)
    transactions.value = txs.data.items
    txTotal.value = txs.data.total
    txLoading.value = false
  } catch {
    loading.value = false
    txLoading.value = false
  }
}

async function doTopUp() {
  submitting.value = true
  try {
    await transactionApi.create({
      accountId: ACCOUNT_ID, type: 'TOP_UP', amount: topUpAmount.value,
      referenceType: 'MANUAL', referenceId: 'TOPUP_' + Date.now(),
      description: '模拟充值',
    })
    showTopUp.value = false
    await loadData()
  } finally { submitting.value = false }
}

async function doConsume() {
  submitting.value = true
  try {
    await transactionApi.create({
      accountId: ACCOUNT_ID, type: 'CONSUME', amount: consumeAmount.value,
      referenceType: consumeRef.value || 'SIMULATE', referenceId: 'CONSUME_' + Date.now(),
      description: consumeRef.value,
    })
    showConsume.value = false
    await loadData()
  } finally { submitting.value = false }
}

async function doRefund() {
  submitting.value = true
  try {
    await transactionApi.create({
      accountId: ACCOUNT_ID, type: 'REFUND', amount: refundAmount.value,
      referenceType: 'MANUAL', referenceId: 'REFUND_' + Date.now(),
      description: refundRef.value || '模拟退款',
    })
    showRefund.value = false
    await loadData()
  } finally { submitting.value = false }
}

onMounted(loadData)
</script>

<style scoped>
.my-account { max-width: 640px; margin: 0 auto; }
.balance-hero {
  background: linear-gradient(135deg, #1c1c1e 0%, #2c2c2e 100%);
  border-radius: 16px;
  padding: 32px;
  color: #f5f5f7;
  text-align: center;
  margin-bottom: 24px;
}
.balance-label { font-size: 11px; text-transform: uppercase; letter-spacing: 2px; color: var(--gold); margin-bottom: 8px; }
.balance-amount { font-size: 42px; font-weight: 300; letter-spacing: -1px; }
.balance-amount.skeleton { opacity: 0.4; }
.currency { font-size: 24px; margin-right: 4px; opacity: 0.7; }
.value { font-weight: 200; }
.balance-meta { margin-top: 12px; display: flex; align-items: center; justify-content: center; gap: 8px; }
.meta-text { font-size: 11px; opacity: 0.5; }
.badge-positive { background: var(--green-bg); color: var(--green); }
.badge-overdue { background: var(--red-bg); color: var(--red); }
.badge-green { background: var(--green-bg); color: var(--green); }
.badge-red { background: var(--red-bg); color: var(--red); }
.badge-blue { background: var(--accent-bg); color: var(--accent); }
.badge-gold { background: var(--gold-bg); color: var(--gold); }

.section-title { font-size: 15px; font-weight: 600; margin-bottom: 12px; }
.action-buttons { display: flex; gap: 8px; margin-bottom: 8px; }
.amount-in { color: var(--green); font-weight: 500; }
.amount-out { color: var(--red); font-weight: 500; }
.empty-hint { text-align: center; color: var(--text-secondary); padding: 32px 0; font-size: 13px; }

/* Modal */
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
  width: 360px;
  box-shadow: 0 8px 40px rgba(0,0,0,0.15);
}
.modal-card h4 { margin-bottom: 16px; font-size: 15px; }
.modal-actions { display: flex; gap: 8px; }
</style>