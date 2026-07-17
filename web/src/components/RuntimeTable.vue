<template>
  <div class="runtime-table">
    <table class="table" v-if="rows.length">
      <thead>
        <tr><th v-for="column in columns" :key="column">{{ label(column) }}</th></tr>
      </thead>
      <tbody>
        <tr v-for="(row, index) in rows" :key="String(row.id ?? index)">
          <td v-for="column in columns" :key="column">
            <span v-if="isStatus(column)" class="badge" :class="statusClass(row[column])">
              {{ display(row[column]) }}
            </span>
            <span v-else>{{ display(row[column]) }}</span>
          </td>
        </tr>
      </tbody>
    </table>
    <div class="empty-hint" v-else>暂无数据</div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { RuntimeRow } from '@/api/platform'

const props = defineProps<{ rows: RuntimeRow[] }>()

const columns = computed(() => {
  const keys = new Set<string>()
  props.rows.slice(0, 10).forEach((row) => {
    Object.keys(row).forEach((key) => {
      if (!['create_user', 'update_user', 'update_time', 'versions', 'items', 'plan'].includes(key)) {
        keys.add(key)
      }
    })
  })
  return [...keys].slice(0, 9)
})

function label(value: string) {
  return value.replaceAll('_', ' ')
}

function isStatus(value: string) {
  return value === 'status' || value.endsWith('_status') || value === 'policy'
}

function statusClass(value: unknown) {
  const text = String(value || '')
  if (['ACTIVE', 'SUCCESS', 'PAID', 'BILLED', 'COMMITTED'].includes(text)) return 'badge-green'
  if (['FAILED', 'CANCELLED', 'EXCEEDED', 'REJECTED'].includes(text)) return 'badge-red'
  return 'badge-blue'
}

function display(value: unknown) {
  if (value === null || value === undefined || value === '') return '-'
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}
</script>

<style scoped>
.runtime-table { width: 100%; overflow-x: auto; }
.empty-hint { text-align: center; color: var(--text-secondary); padding: 28px; }
.badge-green { background: var(--green-bg); color: var(--green); }
.badge-red { background: var(--red-bg); color: var(--red); }
.badge-blue { background: var(--accent-bg); color: var(--accent); }
</style>

