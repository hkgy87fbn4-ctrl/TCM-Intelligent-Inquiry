/**
 * 文献临时库：本标签页内记录「待释放」的 collectionId。
 * - 关闭标签、刷新、前进/离开站点时触发 `pagehide`，用 sendBeacon 发 POST（不依赖 JS 主线程存活）。
 * - 与 Vue 路由守卫互补：SPA 内切换路由仍由文献页 onBeforeRouteLeave 处理。
 */

export const LITERATURE_TAB_COLLECTION_SESSION_KEY =
  'tcm-literature-tab-collection-id'

/**
 * 将当前临时库 ID 写入 sessionStorage；传 null 表示本页已无任何待跟踪的临时库。
 */
export function setLiteratureTabCollectionId(id: string | null) {
  try {
    if (id && id.trim() !== '') {
      sessionStorage.setItem(LITERATURE_TAB_COLLECTION_SESSION_KEY, id.trim())
    } else {
      sessionStorage.removeItem(LITERATURE_TAB_COLLECTION_SESSION_KEY)
    }
  } catch {
    /* 隐私模式等场景下可能不可用，静默忽略 */
  }
}

/**
 * 注册全局 pagehide：在标签页即将卸载时尝试释放 Redis/MySQL 中的临时文献库。
 * 使用 POST 而非 DELETE：sendBeacon 对 DELETE 支持不一致，且空 Body 的 POST 兼容性更好。
 */
export function installLiteraturePagehideBeacon() {
  if (typeof window === 'undefined') return

  window.addEventListener('pagehide', (ev: PageTransitionEvent) => {
    // bfcache 恢复页签时不应删除，否则用户返回后库已被误删
    if (ev.persisted) return

    let cid: string | null = null
    try {
      cid = sessionStorage.getItem(LITERATURE_TAB_COLLECTION_SESSION_KEY)
    } catch {
      return
    }
    if (!cid || typeof navigator.sendBeacon !== 'function') return

    const url = `${window.location.origin}/api/v1/literature/collections/${encodeURIComponent(cid)}/release-beacon`
    const ok = navigator.sendBeacon(url, new Blob([], { type: 'text/plain' }))
    if (ok) {
      try {
        sessionStorage.removeItem(LITERATURE_TAB_COLLECTION_SESSION_KEY)
      } catch {
        /* ignore */
      }
    }
  })
}
