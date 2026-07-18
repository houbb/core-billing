<template>
  <div class="billing-center">
    <div class="page-heading">
      <div>
        <h1>{{ t('billingCenter') }}</h1>
        <p>P1-P9 一体化账户、用量、套餐、支付与账单中心</p>
      </div>
      <button class="btn" @click="load">{{ t('refresh') }}</button>
    </div>

    <nav class="phase-tabs">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        class="phase-tab"
        :class="{ active: active === tab.key }"
        @click="active = tab.key"
      >
        {{ tab.label }}
      </button>
    </nav>

    <div class="card" v-if="loading">{{ t('loading') }}</div>

    <template v-else>
      <section v-show="active === 'balance'" class="metric-grid">
        <article class="metric-card" v-for="row in balances" :key="String(row.id)">
          <span>{{ row.balance_type }}</span>
          <strong>¥{{ money(row.amount) }}</strong>
          <small>冻结 ¥{{ money(row.frozen_amount) }} · {{ row.currency }}</small>
        </article>
      </section>

      <section v-show="active === 'usage'" class="card">
        <RuntimeTable :rows="usage" />
      </section>

      <section v-show="active === 'quota'" class="card">
        <RuntimeTable :rows="quota" />
      </section>

      <section v-show="active === 'plans'" class="plan-grid">
        <article class="card plan-card" v-for="plan in plans" :key="String(plan.id)">
          <span class="badge badge-blue">{{ plan.billing_cycle }}</span>
          <h3>{{ plan.plan_name }}</h3>
          <strong>¥{{ money(plan.price) }}</strong>
          <p>{{ plan.description || '标准商业套餐' }}</p>
          <button class="btn btn-primary" @click="subscribe(String(plan.plan_code))">选择套餐</button>
        </article>
      </section>

      <section v-show="active === 'subscription'" class="card">
        <RuntimeTable :rows="subscription ? [subscription] : []" />
      </section>

      <section v-show="active === 'payments'" class="page-grid">
        <div class="card action-card">
          <h3>创建 MOCK 充值订单</h3>
          <label class="label">账户 ID</label>
          <input class="input" type="number" v-model.number="accountId" />
          <label class="label mt-4">金额</label>
          <input class="input" type="number" min="0.01" step="0.01" v-model.number="topUpAmount" />
          <button class="btn btn-primary mt-4" @click="createPayment">创建订单</button>
        </div>
        <div class="card">
          <RuntimeTable :rows="payments" />
        </div>
      </section>

      <section v-show="active === 'invoices'" class="card">
        <table class="table" v-if="invoices.length">
          <thead>
            <tr><th>账单号</th><th>周期</th><th>金额</th><th>状态</th><th>导出</th></tr>
          </thead>
          <tbody>
            <tr v-for="invoice in invoices" :key="String(invoice.id)">
              <td>{{ invoice.invoice_no }}</td>
              <td>{{ invoice.billing_period }}</td>
              <td>¥{{ money(invoice.total) }}</td>
              <td><span class="badge badge-green">{{ invoice.status }}</span></td>
              <td>
                <a class="btn btn-emphasis" :href="pdfUrl(invoice.id)">PDF</a>
                <a class="btn" :href="excelUrl(invoice.id)">Excel</a>
              </td>
            </tr>
          </tbody>
        </table>
        <div class="empty-hint" v-else>{{ t('empty') }}</div>
      </section>
    </template>

    <p class="error-banner" v-if="error">{{ error }}</p>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import RuntimeTable from '@/components/RuntimeTable.vue'
import { platformApi, type RuntimeRow } from '@/api/platform'
import { t } from '@/i18n'

const active = ref('balance')
const loading = ref(true)
const error = ref('')
const accountId = ref(1)
const topUpAmount = ref(100)
const balances = ref<RuntimeRow[]>([])
const usage = ref<RuntimeRow[]>([])
const quota = ref<RuntimeRow[]>([])
const plans = ref<RuntimeRow[]>([])
const subscription = ref<RuntimeRow | null>(null)
const payments = ref<RuntimeRow[]>([])
const invoices = ref<RuntimeRow[]>([])

const tabs = computed(() => [
  { key: 'balance', label: t('p1') },
  { key: 'usage', label: t('p3') },
  { key: 'quota', label: t('p4') },
  { key: 'plans', label: t('p5') },
  { key: 'subscription', label: '我的订阅' },
  { key: 'payments', label: t('p6') },
  { key: 'invoices', label: t('p7') },
])

function currentMonth() {
  return new Date().toISOString().slice(0, 7)
}

function money(value: unknown) {
  return Number(value || 0).toFixed(2)
}

function pdfUrl(id: unknown) {
  return `/api/v1/billing/invoices/${id}/pdf`
}

function excelUrl(id: unknown) {
  return `/api/v1/billing/invoices/${id}/excel`
}

async function load() {
  loading.value = true
  error.value = ''
  const results = await Promise.allSettled([
    platformApi.balances(accountId.value),
    platformApi.usage(currentMonth()),
    platformApi.quota(),
    platformApi.plans(),
    platformApi.currentSubscription(),
    platformApi.payments(),
    platformApi.invoices(),
  ])
  if (results[0].status === 'fulfilled') balances.value = results[0].value.data
  if (results[1].status === 'fulfilled') usage.value = results[1].value.data
  if (results[2].status === 'fulfilled') quota.value = results[2].value.data
  if (results[3].status === 'fulfilled') plans.value = results[3].value.data
  subscription.value = results[4].status === 'fulfilled' ? results[4].value.data : null
  if (results[5].status === 'fulfilled') payments.value = results[5].value.data
  if (results[6].status === 'fulfilled') invoices.value = results[6].value.data
  loading.value = false
}

async function subscribe(plan: string) {
  try {
    await platformApi.subscribe(plan)
    await load()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '订阅失败'
  }
}

async function createPayment() {
  try {
    await platformApi.createTopUpOrder(accountId.value, topUpAmount.value)
    await load()
    active.value = 'payments'
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '订单创建失败'
  }
}

onMounted(load)
</script>

<style scoped>
.billing-center { width: 100%; }
.page-heading { display: flex; justify-content: space-between; gap: 16px; margin-bottom: 16px; }
.page-heading h1 { font-size: 17px; font-weight: 700; }
.page-heading p { font-size: 11px; color: var(--text-secondary); }
.plan-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 12px; }
.plan-card { display: flex; flex-direction: column; gap: 12px; }
.plan-card h3 { font-size: 17px; }
.plan-card strong { font-size: 28px; font-weight: 600; }
.plan-card p { color: var(--text-secondary); flex: 1; }
.action-card { min-width: 260px; }
.action-card h3 { margin-bottom: 12px; font-size: 15px; }
.badge-blue { background: var(--accent-bg); color: var(--accent); }
.badge-green { background: var(--green-bg); color: var(--green); }
.empty-hint { text-align: center; color: var(--text-secondary); padding: 28px; }
.error-banner { margin-top: 12px; padding: 10px 12px; color: var(--red); background: var(--red-bg); border-radius: 10px; }
</style>

