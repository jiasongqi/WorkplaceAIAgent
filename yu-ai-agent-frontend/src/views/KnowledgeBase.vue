<template>
  <div class="kb-page">
    <!-- Page header -->
    <header class="kb-header">
      <div class="header-left">
        <button type="button" class="back-btn" @click="goBack">← 返回</button>
      </div>
      <div class="header-center">
        <h1 class="page-title">
          <WpIcon name="knowledge" :size="20" class="title-icon" />
          知识库管理
        </h1>
        <p class="page-sub">上传 Markdown / PDF，供 RAG 检索引用</p>
      </div>
      <div class="header-right">
        <span class="doc-badge">{{ visibleDocuments.length }} / {{ documents.length }} 篇</span>
      </div>
    </header>

    <div class="kb-body">
      <!-- Left: upload -->
      <aside class="upload-panel">
        <div class="panel-card">
          <div class="tab-row">
            <button
              type="button"
              class="tab-btn"
              :class="{ active: uploadMode === 'file' }"
              @click="uploadMode = 'file'"
            >文件上传</button>
            <button
              type="button"
              class="tab-btn"
              :class="{ active: uploadMode === 'text' }"
              @click="uploadMode = 'text'"
            >粘贴文本</button>
          </div>

          <div class="field-row">
            <label class="field-label" for="kb-category">文档分类</label>
            <select id="kb-category" v-model="category" class="field-select">
              <option v-for="opt in categoryOptions" :key="opt" :value="opt">{{ opt }}</option>
            </select>
          </div>

          <!-- File upload -->
          <div v-if="uploadMode === 'file'" class="upload-block">
            <div
              class="drop-zone"
              :class="{ dragging: isDragging, uploading }"
              @dragover.prevent
              @drop.prevent="onDrop"
              @dragenter="isDragging = true"
              @dragleave="isDragging = false"
            >
              <div class="drop-icon"><WpIcon name="resume" :size="28" /></div>
              <p class="drop-text">拖拽 .md / .pdf 到此处</p>
              <label class="primary-btn">
                选择文件
                <input
                  ref="fileInputRef"
                  type="file"
                  accept=".md,.pdf"
                  multiple
                  hidden
                  @change="onFileSelect"
                />
              </label>
              <p class="drop-hint">PDF 含表格时将结构化入库（chunkType=table）</p>
            </div>

            <div v-if="uploadQueue.length" class="queue-list">
              <div v-for="item in uploadQueue" :key="item.id" class="queue-item" :class="item.state">
                <span class="queue-name truncate">{{ item.name }}</span>
                <span class="queue-state">{{ queueStateLabel(item.state) }}</span>
              </div>
            </div>
          </div>

          <!-- Text paste -->
          <div v-else class="text-block">
            <input
              v-model="textFilename"
              class="field-input"
              placeholder="文件名，如 notes.md"
              maxlength="120"
            />
            <textarea
              v-model="textContent"
              class="text-area"
              placeholder="粘贴 Markdown 文本内容…"
              rows="8"
            />
            <button
              type="button"
              class="primary-btn full"
              :disabled="textSubmitting || !textContent.trim()"
              @click="submitText"
            >
              {{ textSubmitting ? '提交中…' : '添加到知识库' }}
            </button>
          </div>
        </div>

        <div class="tips-card">
          <div class="tips-title">使用提示</div>
          <ul class="tips-list">
            <li>同一文件内容重复上传会自动去重</li>
            <li>分类标签会写入向量元数据，便于后续过滤</li>
            <li>失败文档可点「重新上传」选择原文件</li>
          </ul>
        </div>
      </aside>

      <!-- Right: list -->
      <section class="list-panel">
        <div class="list-toolbar">
          <div class="filter-chips">
            <button
              v-for="f in filterOptions"
              :key="f.key"
              type="button"
              class="chip"
              :class="{ active: statusFilter === f.key }"
              @click="statusFilter = f.key"
            >{{ f.label }}</button>
          </div>
          <div class="toolbar-r">
            <input
              v-model="searchQuery"
              class="search-input"
              type="search"
              placeholder="搜索文件名…"
            />
            <button type="button" class="ghost-btn" :disabled="loading" @click="loadDocuments">
              {{ loading ? '刷新中…' : '刷新' }}
            </button>
          </div>
        </div>

        <div v-if="banner" class="banner" :class="banner.type">{{ banner.text }}</div>

        <div v-if="loading" class="empty-state">加载中…</div>
        <div v-else-if="visibleDocuments.length === 0" class="empty-state">
          {{ documents.length ? '没有符合筛选条件的文档' : '暂无文档，请在左侧上传 Markdown 或 PDF' }}
        </div>

        <div v-else class="doc-grid">
          <article
            v-for="doc in visibleDocuments"
            :key="doc.docId"
            class="doc-card"
            :class="statusClass(doc.status)"
          >
            <div class="doc-card-top">
              <span class="file-type-badge">{{ fileType(doc.fileName) }}</span>
              <span class="status-pill" :class="statusClass(doc.status)">
                {{ statusLabel(doc.status) }}
              </span>
            </div>

            <h3 class="doc-name truncate" :title="doc.fileName">{{ doc.fileName }}</h3>

            <div class="doc-meta">
              <span>{{ formatSize(doc.fileSize) }}</span>
              <span v-if="doc.retryCount > 0">重试 {{ doc.retryCount }} 次</span>
            </div>
            <div class="doc-times">
              <span>上传 {{ formatTime(doc.uploadedAt) }}</span>
              <span v-if="doc.indexedAt">索引 {{ formatTime(doc.indexedAt) }}</span>
            </div>
            <p v-if="doc.failReason" class="doc-error">{{ doc.failReason }}</p>

            <div class="doc-actions">
              <button
                v-if="doc.status === 'FAILED_RETRYABLE' || doc.status === 'FAILED_FINAL'"
                type="button"
                class="action-btn retry"
                @click="promptRetry(doc)"
              >重新上传</button>
              <button
                v-if="doc.status !== 'DELETED'"
                type="button"
                class="action-btn danger"
                @click="confirmDelete(doc)"
              >删除</button>
            </div>
          </article>
        </div>
      </section>
    </div>

    <!-- Hidden retry file input -->
    <input
      ref="retryInputRef"
      type="file"
      accept=".md,.pdf"
      hidden
      @change="onRetryFileSelect"
    />

    <!-- Delete modal -->
    <div v-if="deleteTarget" class="modal-overlay" @click.self="deleteTarget = null">
      <div class="modal-card">
        <h3 class="modal-title">确认删除</h3>
        <p class="modal-text">删除文档「{{ deleteTarget.fileName }}」？</p>
        <p class="modal-hint">删除后 RAG 将不再引用该文档（软删除，向量暂未自动清理）。</p>
        <div class="modal-actions">
          <button type="button" class="ghost-btn" @click="deleteTarget = null">取消</button>
          <button type="button" class="danger-btn" :disabled="deleting" @click="doDelete">
            {{ deleting ? '删除中…' : '确认删除' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import WpIcon from '../components/WpIcon.vue'
import {
  listDocuments,
  uploadDocument,
  deleteDocument,
  addTextDocument
} from '../api'

const router = useRouter()

const documents = ref([])
const loading = ref(false)
const deleting = ref(false)
const uploading = ref(false)
const textSubmitting = ref(false)
const isDragging = ref(false)
const uploadMode = ref('file')
const category = ref('通用')
const statusFilter = ref('all')
const searchQuery = ref('')
const banner = ref(null)
const deleteTarget = ref(null)
const retryTarget = ref(null)
const textContent = ref('')
const textFilename = ref('notes.md')
const uploadQueue = ref([])

const fileInputRef = ref(null)
const retryInputRef = ref(null)

const categoryOptions = ['通用', '职场', '简历', '面试', '技能', '其他']
const filterOptions = [
  { key: 'all', label: '全部' },
  { key: 'ready', label: '已就绪' },
  { key: 'processing', label: '处理中' },
  { key: 'failed', label: '失败' }
]

const goBack = () => router.push('/')

const showBanner = (type, text, ms = 4000) => {
  banner.value = { type, text }
  if (ms > 0) {
    setTimeout(() => {
      if (banner.value?.text === text) banner.value = null
    }, ms)
  }
}

const loadDocuments = async () => {
  loading.value = true
  try {
    const res = await listDocuments()
    const list = res.data?.data || []
    documents.value = list.sort((a, b) => {
      const rank = (s) => {
        if (s === 'INDEXED') return 0
        if (s?.startsWith('FAILED')) return 2
        if (s === 'DELETED') return 3
        return 1
      }
      const dr = rank(a.status) - rank(b.status)
      if (dr !== 0) return dr
      return new Date(b.uploadedAt || 0) - new Date(a.uploadedAt || 0)
    })
  } catch (e) {
    const msg = e.response?.data?.message || '加载文档列表失败'
    showBanner('error', msg, 6000)
  } finally {
    loading.value = false
  }
}

const visibleDocuments = computed(() => {
  let list = documents.value.filter(d => d.status !== 'DELETED')
  if (statusFilter.value === 'ready') {
    list = list.filter(d => d.status === 'INDEXED')
  } else if (statusFilter.value === 'processing') {
    list = list.filter(d => ['UPLOADING', 'PARSING', 'EMBEDDING', 'INDEXING'].includes(d.status))
  } else if (statusFilter.value === 'failed') {
    list = list.filter(d => d.status?.startsWith('FAILED'))
  }
  const q = searchQuery.value.trim().toLowerCase()
  if (q) list = list.filter(d => (d.fileName || '').toLowerCase().includes(q))
  return list
})

const queueStateLabel = (state) => {
  if (state === 'pending') return '等待'
  if (state === 'uploading') return '上传中'
  if (state === 'done') return '完成'
  if (state === 'error') return '失败'
  return state
}

const uploadFile = async (file) => {
  const lower = file.name.toLowerCase()
  if (!lower.endsWith('.md') && !lower.endsWith('.pdf')) {
    showBanner('error', `不支持的文件：${file.name}（仅 .md / .pdf）`)
    return
  }
  const item = { id: `${Date.now()}-${file.name}`, name: file.name, state: 'uploading' }
  uploadQueue.value.push(item)
  uploading.value = true
  try {
    await uploadDocument(file, category.value)
    item.state = 'done'
    showBanner('success', `✓ ${file.name} 上传成功`)
    await loadDocuments()
  } catch (e) {
    item.state = 'error'
    const msg = e.response?.data?.message || `${file.name} 上传失败`
    showBanner('error', msg, 6000)
  } finally {
    uploading.value = uploadQueue.value.some(i => i.state === 'uploading')
    setTimeout(() => {
      uploadQueue.value = uploadQueue.value.filter(i => i.state === 'uploading')
    }, 3000)
  }
}

const onFileSelect = (e) => {
  const files = e.target.files
  for (const file of files) uploadFile(file)
  e.target.value = ''
}

const onDrop = (e) => {
  isDragging.value = false
  for (const file of e.dataTransfer.files) uploadFile(file)
}

const submitText = async () => {
  const content = textContent.value.trim()
  if (!content) return
  let filename = textFilename.value.trim() || 'notes.md'
  if (!filename.endsWith('.md')) filename += '.md'

  textSubmitting.value = true
  try {
    await addTextDocument(content, filename, category.value)
    showBanner('success', `✓ ${filename} 已添加`)
    textContent.value = ''
    await loadDocuments()
  } catch (e) {
    const msg = e.response?.data?.message || '文本添加失败'
    showBanner('error', msg, 6000)
  } finally {
    textSubmitting.value = false
  }
}

const confirmDelete = (doc) => {
  deleteTarget.value = doc
}

const doDelete = async () => {
  if (!deleteTarget.value) return
  deleting.value = true
  try {
    await deleteDocument(deleteTarget.value.docId)
    showBanner('success', `已删除 ${deleteTarget.value.fileName}`)
    deleteTarget.value = null
    await loadDocuments()
  } catch (e) {
    const msg = e.response?.data?.message || '删除失败'
    showBanner('error', msg, 6000)
  } finally {
    deleting.value = false
  }
}

const promptRetry = (doc) => {
  retryTarget.value = doc
  showBanner('info', `请选择原文件「${doc.fileName}」重新上传`, 5000)
  retryInputRef.value?.click()
}

const onRetryFileSelect = (e) => {
  const file = e.target.files?.[0]
  e.target.value = ''
  if (!file || !retryTarget.value) return
  uploadFile(file)
  retryTarget.value = null
}

const statusLabel = (status) => {
  const map = {
    UPLOADING: '上传中',
    PARSING: '解析中',
    EMBEDDING: '向量化中',
    INDEXING: '索引中',
    INDEXED: '已就绪',
    FAILED_RETRYABLE: '可重试',
    FAILED_FINAL: '失败',
    DELETED: '已删除'
  }
  return map[status] || status
}

const statusClass = (status) => {
  if (status === 'INDEXED') return 'indexed'
  if (status?.startsWith('FAILED')) return 'failed'
  if (status === 'DELETED') return 'deleted'
  return 'processing'
}

const fileType = (name) => {
  if (!name) return '—'
  const lower = name.toLowerCase()
  if (lower.endsWith('.pdf')) return 'PDF'
  if (lower.endsWith('.md')) return 'MD'
  return 'FILE'
}

const formatSize = (bytes) => {
  if (!bytes) return '—'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

const formatTime = (value) => {
  if (!value) return '—'
  const d = new Date(value)
  if (isNaN(d.getTime())) return value
  return d.toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

onMounted(loadDocuments)
</script>

<style scoped>
.kb-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
  color: var(--t1);
}

/* Header */
.kb-header {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  gap: 12px;
  padding: 14px 24px;
  flex-shrink: 0;
  background: var(--topbar-bg);
  backdrop-filter: blur(var(--glass-blur));
  -webkit-backdrop-filter: blur(var(--glass-blur));
  border-bottom: 1px solid var(--glass-border);
  transition: background 0.35s var(--ease), border-color 0.35s var(--ease);
}

.header-left { justify-self: start; }
.header-right { justify-self: end; }
.header-center { text-align: center; }

.back-btn {
  font-size: 14px;
  color: var(--t2);
  padding: 6px 10px;
  border-radius: var(--r-sm);
  transition: all 0.2s var(--ease);
}
.back-btn:hover {
  color: var(--t1);
  background: var(--glass-hover);
}

.page-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
  font-weight: 700;
  margin: 0;
  color: var(--t1);
}
.title-icon { color: var(--gold-text); }

.page-sub {
  margin: 2px 0 0;
  font-size: 12px;
  color: var(--t3);
}

.doc-badge {
  font-size: 12px;
  font-weight: 500;
  color: var(--gold-text);
  background: var(--gold-soft);
  border: 1px solid var(--gold-border-soft);
  padding: 4px 12px;
  border-radius: var(--r-full);
}

/* Body layout */
.kb-body {
  flex: 1;
  display: grid;
  grid-template-columns: minmax(280px, 340px) 1fr;
  gap: 16px;
  padding: 16px 20px 20px;
  overflow: hidden;
  min-height: 0;
}

.upload-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
  overflow-y: auto;
  min-height: 0;
}

.panel-card,
.tips-card,
.list-panel {
  background: var(--layer1);
  border: 1px solid var(--glass-border);
  border-radius: var(--r-md);
  box-shadow: var(--shadow-card);
  transition: background 0.35s var(--ease), border-color 0.35s var(--ease), box-shadow 0.35s var(--ease);
}

.panel-card { padding: 16px; }

.tips-card { padding: 14px 16px; }
.tips-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--t2);
  margin-bottom: 8px;
}
.tips-list {
  margin: 0;
  padding-left: 18px;
  font-size: 12px;
  color: var(--t3);
  line-height: 1.7;
}

/* Tabs */
.tab-row {
  display: flex;
  gap: 4px;
  margin-bottom: 14px;
  padding: 3px;
  background: var(--layer2);
  border-radius: var(--r-sm);
  border: 1px solid var(--glass-border);
}

.tab-btn {
  flex: 1;
  padding: 7px 10px;
  font-size: 13px;
  font-weight: 500;
  color: var(--t3);
  border-radius: calc(var(--r-sm) - 2px);
  transition: all 0.2s var(--ease);
}
.tab-btn:hover { color: var(--t2); }
.tab-btn.active {
  background: var(--gold-soft);
  color: var(--gold-text);
  box-shadow: 0 1px 4px var(--gold-dim);
}

/* Fields */
.field-row { margin-bottom: 12px; }
.field-label {
  display: block;
  font-size: 12px;
  color: var(--t3);
  margin-bottom: 6px;
}
.field-select,
.field-input,
.search-input {
  width: 100%;
  padding: 8px 12px;
  font-size: 13px;
  color: var(--t1);
  background: var(--layer2);
  border: 1px solid var(--glass-border);
  border-radius: var(--r-sm);
  outline: none;
  transition: border-color 0.2s var(--ease), box-shadow 0.2s var(--ease);
}
.field-select:focus,
.field-input:focus,
.search-input:focus {
  border-color: var(--gold-border);
  box-shadow: 0 0 0 3px var(--gold-dim);
}

.text-area {
  width: 100%;
  margin: 10px 0;
  padding: 10px 12px;
  font-size: 13px;
  line-height: 1.55;
  color: var(--t1);
  background: var(--layer2);
  border: 1px solid var(--glass-border);
  border-radius: var(--r-sm);
  resize: vertical;
  min-height: 140px;
  outline: none;
}
.text-area:focus {
  border-color: var(--gold-border);
  box-shadow: 0 0 0 3px var(--gold-dim);
}

/* Drop zone */
.drop-zone {
  border: 2px dashed var(--glass-border);
  border-radius: var(--r-md);
  padding: 24px 16px;
  text-align: center;
  background: var(--layer2);
  transition: all 0.25s var(--ease);
}
.drop-zone.dragging,
.drop-zone.uploading {
  border-color: var(--gold-border);
  background: var(--gold-dim);
  box-shadow: 0 0 0 4px var(--gold-dim);
}
.drop-icon {
  display: flex;
  justify-content: center;
  margin-bottom: 8px;
  color: var(--gold-text);
}
.drop-text {
  font-size: 13px;
  color: var(--t2);
  margin-bottom: 10px;
}
.drop-hint {
  font-size: 11px;
  color: var(--t3);
  margin-top: 10px;
}

.primary-btn {
  display: inline-block;
  padding: 8px 18px;
  font-size: 13px;
  font-weight: 600;
  color: var(--abyss);
  background: var(--gold-grad);
  border-radius: var(--r-sm);
  cursor: pointer;
  box-shadow: 0 4px 14px var(--gold-glow);
  transition: transform 0.2s var(--ease), box-shadow 0.2s var(--ease);
}
.primary-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 6px 20px var(--gold-glow-strong);
}
.primary-btn:disabled { opacity: 0.55; cursor: not-allowed; }
.primary-btn.full { width: 100%; text-align: center; border: none; }

[data-theme="sage"] .primary-btn { color: #fff; }

.ghost-btn {
  padding: 7px 14px;
  font-size: 13px;
  color: var(--t2);
  background: var(--glass-hover);
  border: 1px solid var(--glass-border);
  border-radius: var(--r-sm);
  transition: all 0.2s var(--ease);
}
.ghost-btn:hover:not(:disabled) {
  color: var(--t1);
  border-color: var(--gold-border-soft);
  background: var(--gold-soft);
}
.ghost-btn:disabled { opacity: 0.5; cursor: not-allowed; }

.danger-btn {
  padding: 8px 16px;
  font-size: 13px;
  font-weight: 600;
  color: #fff;
  background: var(--danger);
  border-radius: var(--r-sm);
  transition: opacity 0.2s;
}
.danger-btn:hover:not(:disabled) { opacity: 0.9; }
.danger-btn:disabled { opacity: 0.55; cursor: not-allowed; }

/* Upload queue */
.queue-list { margin-top: 12px; display: flex; flex-direction: column; gap: 6px; }
.queue-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  font-size: 12px;
  border-radius: var(--r-sm);
  background: var(--layer2);
  border: 1px solid var(--glass-border);
}
.queue-item.uploading { border-color: var(--gold-border-soft); }
.queue-item.done { border-color: var(--ok); color: var(--ok); }
.queue-item.error { border-color: var(--danger); color: var(--danger); }
.queue-name { flex: 1; color: var(--t2); }

/* List panel */
.list-panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
  padding: 0;
}

.list-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 14px 16px;
  border-bottom: 1px solid var(--glass-border);
  flex-shrink: 0;
}

.filter-chips { display: flex; flex-wrap: wrap; gap: 6px; }

.chip {
  padding: 5px 12px;
  font-size: 12px;
  font-weight: 500;
  color: var(--t3);
  border: 1px solid var(--glass-border);
  border-radius: var(--r-full);
  background: transparent;
  transition: all 0.2s var(--ease);
}
.chip:hover { color: var(--t2); border-color: var(--chip-hover-border); }
.chip.active {
  color: var(--gold-text);
  background: var(--gold-soft);
  border-color: var(--gold-border);
}

.toolbar-r {
  display: flex;
  align-items: center;
  gap: 8px;
}
.search-input { width: 180px; }

.banner {
  margin: 12px 16px 0;
  padding: 10px 14px;
  font-size: 13px;
  border-radius: var(--r-sm);
  flex-shrink: 0;
}
.banner.success {
  color: var(--ok);
  background: var(--ok-bg);
  border: 1px solid rgba(52, 211, 153, 0.25);
}
.banner.error {
  color: var(--danger);
  background: var(--danger-bg);
  border: 1px solid rgba(248, 113, 113, 0.25);
}
.banner.info {
  color: var(--gold-text);
  background: var(--gold-soft);
  border: 1px solid var(--gold-border-soft);
}

.empty-state {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px 20px;
  font-size: 14px;
  color: var(--t3);
}

.doc-grid {
  flex: 1;
  overflow-y: auto;
  padding: 12px 16px 16px;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 12px;
  align-content: start;
}

.doc-card {
  padding: 14px;
  border-radius: var(--r-md);
  border: 1px solid var(--glass-border);
  background: var(--layer2);
  transition: border-color 0.2s var(--ease), box-shadow 0.2s var(--ease);
}
.doc-card:hover {
  border-color: var(--card-hover-border);
  box-shadow: var(--card-hover-shadow);
}
.doc-card.indexed { border-left: 3px solid var(--ok); }
.doc-card.failed { border-left: 3px solid var(--danger); }
.doc-card.processing { border-left: 3px solid var(--gold); }

.doc-card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.file-type-badge {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.04em;
  color: var(--gold-text);
  background: var(--gold-soft);
  padding: 2px 8px;
  border-radius: 4px;
}

.status-pill {
  font-size: 11px;
  font-weight: 500;
  padding: 2px 8px;
  border-radius: var(--r-full);
}
.status-pill.indexed { color: var(--ok); background: var(--ok-bg); }
.status-pill.processing { color: var(--gold-text); background: var(--gold-soft); }
.status-pill.failed { color: var(--danger); background: var(--danger-bg); }

.doc-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--t1);
  margin: 0 0 6px;
}

.doc-meta,
.doc-times {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  font-size: 11px;
  color: var(--t3);
}
.doc-times { margin-top: 4px; }

.doc-error {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--danger);
  line-height: 1.45;
}

.doc-actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid var(--glass-border);
}

.action-btn {
  flex: 1;
  padding: 6px 10px;
  font-size: 12px;
  font-weight: 500;
  border-radius: var(--r-sm);
  border: 1px solid var(--glass-border);
  color: var(--t2);
  background: var(--glass-hover);
  transition: all 0.2s var(--ease);
}
.action-btn.retry:hover {
  color: var(--gold-text);
  border-color: var(--gold-border);
  background: var(--gold-soft);
}
.action-btn.danger:hover {
  color: var(--danger);
  border-color: rgba(248, 113, 113, 0.35);
  background: var(--danger-bg);
}

/* Modal */
.modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--overlay-bg);
  backdrop-filter: blur(6px);
}

.modal-card {
  width: min(400px, 92vw);
  padding: 22px;
  background: var(--layer1);
  border: 1px solid var(--glass-border);
  border-radius: var(--r-lg);
  box-shadow: var(--card-hover-shadow);
}

.modal-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--t1);
  margin: 0 0 8px;
}
.modal-text {
  font-size: 14px;
  color: var(--t2);
  margin: 0 0 6px;
}
.modal-hint {
  font-size: 12px;
  color: var(--t3);
  margin: 0 0 18px;
  line-height: 1.5;
}
.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.truncate {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 900px) {
  .kb-body {
    grid-template-columns: 1fr;
    overflow-y: auto;
  }
  .upload-panel { overflow: visible; }
  .list-panel { min-height: 420px; }
  .kb-header {
    grid-template-columns: auto 1fr;
    grid-template-rows: auto auto;
  }
  .header-center { grid-column: 1 / -1; order: -1; }
  .header-right { justify-self: end; }
  .search-input { width: 140px; }
}
</style>
