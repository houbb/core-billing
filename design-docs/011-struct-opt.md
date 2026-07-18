# 目标

期望前后端分离。代码的结构也要保持独立性。

~\pom.xml、src 这个文件不应该直接放在根目录下，这些属于后端的东西。可以放在 core-ai-backend 的文件夹（子项目）下面。

优化一下目录结构

并且测试验证一下功能的正确性。

---

# 执行结果

目录结构优化已完成：

- `pom.xml` + `src/` + `data/` → 移至 `core-billing-backend/`
- `web/` → 重命名为 `core-billing-frontend/`
- `vite.config.ts` 中 `outDir` 更新为 `../core-billing-backend/src/main/resources/static`
- `.gitignore` 新增 `core-billing-backend/data/` 规则
- `AGENTS.md` 更新为最终文件夹名称（`core-billing-backend` / `core-billing-frontend`）

## 验证结果
- [x] 后端测试: `mvn test` from `core-billing-backend/` — **33 tests passed, 0 failures, BUILD SUCCESS**
- [x] 前端构建: `npm run build` from `core-billing-frontend/` — **built in 4.54s, 输出到 ../core-billing-backend/src/main/resources/static/**