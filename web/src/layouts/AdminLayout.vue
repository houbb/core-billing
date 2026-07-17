<template>
  <div class="admin-layout">
    <aside class="sidebar">
      <div class="sidebar-logo">商业中心</div>
      <nav class="sidebar-nav">
        <router-link to="/admin/accounts">账户管理</router-link>
        <router-link to="/admin/transactions">交易流水</router-link>
        <router-link to="/admin/platform/p1">P1-P9 商业平台</router-link>
        <router-link to="/account">← 返回用户端</router-link>
      </nav>
    </aside>
    <main class="admin-main">
      <router-view />
    </main>
  </div>
</template>

<script setup lang="ts">
</script>

<style scoped>
.admin-layout { display: flex; min-height: 100vh; }
.sidebar {
  width: 220px;
  background: var(--bg-dark);
  color: var(--text-light);
  padding: 24px 0;
  flex-shrink: 0;
}
.sidebar-logo {
  padding: 0 20px 20px;
  font-size: 15px;
  font-weight: 700;
  border-bottom: 1px solid rgba(255,255,255,0.1);
  margin-bottom: 12px;
}
.sidebar-nav { display: flex; flex-direction: column; }
.sidebar-nav a {
  padding: 10px 20px;
  color: rgba(255,255,255,0.65);
  font-size: 13px;
  transition: all 0.15s;
}
.sidebar-nav a:hover,
.sidebar-nav a.router-link-active {
  color: var(--text-light);
  background: rgba(255,255,255,0.08);
}
.admin-main {
  flex: 1;
  width: 100%;
  padding: 24px 32px;
  overflow-y: auto;
}
</style>
