<template>
  <div class="user-layout">
    <header class="user-header">
      <div class="header-inner">
        <span class="logo">Core Platform</span>
        <nav class="nav-links">
          <router-link to="/account">费用中心</router-link>
        </nav>
      </div>
    </header>
    <main class="user-main">
      <router-view />
    </main>
  </div>
</template>

<script setup lang="ts">
</script>

<style scoped>
.user-layout { min-height: 100vh; background: var(--bg-secondary); }
.user-header {
  background: var(--bg-primary);
  border-bottom: 1px solid var(--border);
  padding: 0 24px;
  height: 48px;
  display: flex;
  align-items: center;
}
.header-inner {
  width: 100%;
  max-width: 800px;
  margin: 0 auto;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.logo { font-size: 15px; font-weight: 700; color: var(--text-primary); }
.nav-links { display: flex; gap: 20px; }
.nav-links a { font-size: 13px; color: var(--text-secondary); }
.nav-links a.router-link-active { color: var(--accent); }
.user-main { max-width: 800px; margin: 24px auto; padding: 0 16px; }
</style>