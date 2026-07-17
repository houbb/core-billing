<template>
  <div class="platform-console">
    <div class="page-heading">
      <div>
        <h1>{{ t('adminConsole') }}</h1>
        <p>P1-P9 Runtime 控制台 · 配置、模拟、审计和经营分析</p>
      </div>
      <button class="btn" @click="load">{{ t('refresh') }}</button>
    </div>

    <nav class="phase-tabs">
      <router-link
        v-for="phase in phases"
        :key="phase.key"
        class="phase-tab"
        :to="`/admin/platform/${phase.key}`"
      >
        {{ phase.label }}
      </router-link>
    </nav>

    <section class="page-grid">
      <div class="card operation-card">
        <div class="flex-between mb-4">
          <div>
            <span class="badge badge-blue">{{ current.label }}</span>
            <h2>{{ current.title }}</h2>
          </div>
          <button class="btn btn-primary" @click="execute" :disabled="executing">
            {{ executing ? t('loading') : t('execute') }}
          </button>
        </div>
        <p>{{ current.description }}</p>
        <label class="label mt-4">操作 JSON</label>
        <textarea class="input json-editor" v-model="payload"></textarea>
        <p class="result-banner" v-if="result">{{ result }}</p>
      </div>

      <div class="card data-card">
        <div class="flex-between mb-4">
          <h2>运行数据</h2>
          <span class="badge badge-green">{{ rows.length }} records</span>
        </div>
        <RuntimeTable :rows="rows" />
        <pre v-if="dashboard" class="dashboard-json">{{ dashboard }}</pre>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import RuntimeTable from '@/components/RuntimeTable.vue'
import { platformApi, type RuntimeRow } from '@/api/platform'
import { t } from '@/i18n'

interface PhaseConfig {
  key: string
  label: string
  title: string
  description: string
  get: string
  post: string
  template: RuntimeRow
  dashboard?: boolean
}

const phases: PhaseConfig[] = [
  {
    key: 'p1', label: t('p1'), title: '余额预充值',
    description: '维护可用余额、冻结余额与两阶段扣款。',
    get: '/admin/accounts', post: '/accounts/1/deposit',
    template: { amount: 100, referenceId: 'ADMIN_DEPOSIT_001', description: '运营充值' },
  },
  {
    key: 'p2', label: t('p2'), title: '价格规则',
    description: '创建资源价格、版本和阶梯计费规则。',
    get: '/admin/pricing/rules', post: '/admin/pricing/rules',
    template: { resourceCode: 'AI_TOKEN', ruleName: 'GPT-5 标准价', pricingMode: 'UNIT', unitQuantity: 1000, price: 0.01, condition: { model: 'GPT5' } },
  },
  {
    key: 'p3', label: t('p3'), title: 'Usage Event',
    description: '上报资源用量并联动计价、账本与每日聚合。',
    get: '/usage?tenantId=default', post: '/usage/events',
    template: { eventId: 'usage-web-001', resource: 'AI_TOKEN', quantity: 1000, unit: 'TOKEN', metadata: { model: 'GPT5' }, chargeBalance: false },
  },
  {
    key: 'p4', label: t('p4'), title: '额度分配',
    description: '配置套餐额度、阻断/超额/降级策略和告警。',
    get: '/quota/default', post: '/admin/quota/allocations',
    template: { tenantId: 'default', resourceCode: 'AI_TOKEN', quotaTotal: 1000000, policy: 'BLOCK' },
  },
  {
    key: 'p5', label: t('p5'), title: '套餐管理',
    description: '创建产品套餐、版本、试用和订阅生命周期。',
    get: '/admin/subscription/plans', post: '/admin/subscription/plans',
    template: { productId: 1, planCode: 'PRO', planName: 'Pro', billingCycle: 'MONTHLY', price: 99, trialDays: 7, items: [{ itemType: 'QUOTA', resourceCode: 'AI_TOKEN', value: 1000000, unit: 'TOKEN' }] },
  },
  {
    key: 'p6', label: t('p6'), title: '支付订单',
    description: '创建可验签 MOCK 支付订单，支持回调、退款与对账。',
    get: '/admin/payments/orders', post: '/payments/orders',
    template: { businessType: 'TOP_UP', businessId: '1', accountId: 1, amount: 100, channelCode: 'MOCK', idempotencyKey: 'PAY_WEB_001' },
  },
  {
    key: 'p7', label: t('p7'), title: '账单生成',
    description: '汇总 Usage 与 Subscription，生成 Invoice、Statement 和导出文件。',
    get: '/admin/invoices', post: '/admin/invoices/generate',
    template: { tenantId: 'default', period: new Date().toISOString().slice(0, 7), country: 'CN' },
  },
  {
    key: 'p8', label: t('p8'), title: '财务快照',
    description: '生成 Revenue、Profit、MRR、ARR、客户与产品分析。',
    get: '/admin/finance/dashboard', post: '/admin/finance/snapshots',
    template: {}, dashboard: true,
  },
  {
    key: 'p9', label: t('p9'), title: '企业合同',
    description: '合同、多组织、多币种、营销、市场、伙伴、预算、成本中心、审批和分账。',
    get: '/admin/enterprise/dashboard', post: '/admin/enterprise/contracts',
    template: { customer: '示例企业', amount: 100000, paymentTerm: 'NET30' },
    dashboard: true,
  },
]

const route = useRoute()
const rows = ref<RuntimeRow[]>([])
const dashboard = ref('')
const payload = ref('')
const result = ref('')
const executing = ref(false)

const current = computed(() => phases.find((phase) => phase.key === route.params.phase) ?? phases[0])

function resetPayload() {
  payload.value = JSON.stringify(current.value.template, null, 2)
  result.value = ''
}

async function load() {
  dashboard.value = ''
  try {
    const response = await platformApi.adminGet(current.value.get)
    if (Array.isArray(response.data)) {
      rows.value = response.data
    } else if (response.data?.items && Array.isArray(response.data.items)) {
      rows.value = response.data.items
    } else {
      rows.value = []
      dashboard.value = JSON.stringify(response.data, null, 2)
    }
  } catch (cause) {
    rows.value = []
    result.value = cause instanceof Error ? cause.message : '加载失败'
  }
}

async function execute() {
  executing.value = true
  result.value = ''
  try {
    const body = payload.value.trim() ? JSON.parse(payload.value) : {}
    const response = await platformApi.adminPost(current.value.post, body)
    result.value = JSON.stringify(response.data, null, 2)
    await load()
  } catch (cause) {
    result.value = cause instanceof Error ? cause.message : '执行失败'
  } finally {
    executing.value = false
  }
}

watch(current, () => {
  resetPayload()
  load()
})

onMounted(() => {
  resetPayload()
  load()
})
</script>

<style scoped>
.platform-console { width: 100%; }
.page-heading { display: flex; justify-content: space-between; gap: 16px; margin-bottom: 16px; }
.page-heading h1 { font-size: 17px; font-weight: 700; }
.page-heading p, .operation-card > p { font-size: 11px; color: var(--text-secondary); }
.operation-card, .data-card { min-width: 0; }
.operation-card h2, .data-card h2 { font-size: 17px; margin-top: 8px; }
.json-editor { min-height: 300px; resize: vertical; font-family: ui-monospace, SFMono-Regular, Consolas, monospace; }
.result-banner, .dashboard-json {
  margin-top: 12px; padding: 12px; border-radius: 10px; overflow: auto;
  white-space: pre-wrap; background: var(--bg-secondary); color: var(--text-secondary);
}
.badge-blue { background: var(--accent-bg); color: var(--accent); }
.badge-green { background: var(--green-bg); color: var(--green); }
</style>

