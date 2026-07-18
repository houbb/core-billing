<template>
  <div class="account-list-page">
    <div class="flex-between mb-4">
      <h2>账户管理</h2>
      <button class="btn btn-primary" @click="showCreate = true">创建账户</button>
    </div>

    <div class="modal-overlay" v-if="showCreate" @click.self="showCreate = false">
      <div class="modal-card">
        <h4>创建商业账户</h4>
        <label class="label">账户名称</label>
        <input class="input" v-model="newName" placeholder="如：Echo个人账户" />
        <label class="label mt-4">账户类型</label>
        <select class="input" v-model="newType">
          <option value="PERSONAL">个人账户</option>
          <option value="ORGANIZATION">企业账户</option>
        </select>
        <div class="modal-actions mt-4">
          <button class="btn btn-primary" @click="doCreate" :disabled="creating">确认创建</button>
          <button class="btn" @click="showCreate = false">取消</button>
        </div>
      </div>
    </div>

    <div class="card" v-if="!loading">
      <table class="table" v-if="accounts.length > 0">
        <thead>
          <tr>
            <th>账户</th>
            <th>类型</th>
            <th>状态</th>
            <th>创建时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="acc in accounts" :key="acc.id">
            <td>{{ acc.accountName }}</td>
            <td>
              <span class="badge" :class="acc.accountType === 'PERSONAL' ? 'badge-blue' : 'badge-gold'">
                {{ acc.accountType === 'PERSONAL' ? '个人' : '企业' }}
              </span>
            </td>
            <td>
              <span class="badge" :class="acc.status === 'ACTIVE' ? 'badge-green' : 'badge-red'">
                {{ acc.status }}
              </span>
            </td>
            <td>{{ formatTime(acc.createTime) }}</td>
            <td>
              <router-link :to="'/admin/accounts/' + acc.id" class="btn btn-emphasis" style="font-size:11px;padding:4px 10px">
                详情
              </router-link>
            </td>
          </tr>
        </tbody>
      </table>
      <div class="empty-hint" v-else>暂无账户</div>
    </div>
    <div class="card" v-else><div class="empty-hint">加载中...</div></div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { accountApi, type Account } from '@/api/account'

const accounts = ref<Account[]>([])
const loading = ref(true)
const showCreate = ref(false)
const newName = ref('')
const newType = ref('PERSONAL')
const creating = ref(false)

function formatTime(t: string) {
  if (!t) return '-'
  return t.replace('T', ' ').substring(0, 10)
}

async function load() {
  loading.value = true
  try {
    const res = await accountApi.listAdmin(1, 100)
    accounts.value = res.data.items
  } finally { loading.value = false }
}

async function doCreate() {
  if (!newName.value) return
  creating.value = true
  try {
    await accountApi.create(newName.value, newType.value)
    showCreate.value = false
    newName.value = ''
    await load()
  } finally { creating.value = false }
}

onMounted(load)
</script>

<style scoped>
h2 { font-size: 17px; font-weight: 700; }
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
.empty-hint { text-align: center; color: var(--text-secondary); padding: 32px 0; font-size: 13px; }
.badge-blue { background: var(--accent-bg); color: var(--accent); }
.badge-gold { background: var(--gold-bg); color: var(--gold); }
.badge-green { background: var(--green-bg); color: var(--green); }
.badge-red { background: var(--red-bg); color: var(--red); }
</style>