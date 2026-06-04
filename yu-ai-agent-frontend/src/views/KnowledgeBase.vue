<template>
  <div class="kb-layout">
    <!-- Header -->
    <div class="header">
      <div class="back-button" @click="goBack">← 返回</div>
      <h1 class="title">📚 知识库管理</h1>
      <div class="doc-count">{{ documents.length }} 个文档</div>
    </div>

    <div class="content">
      <!-- Upload area -->
      <section class="upload-section">
        <div class="upload-zone" @dragover.prevent @drop.prevent="onDrop"
             :class="{ dragging: isDragging }"
             @dragenter="isDragging = true" @dragleave="isDragging = false">
          <div class="upload-icon">📄</div>
          <p class="upload-text">拖拽 Markdown 文件到此处，或</p>
          <label class="upload-btn">
            选择文件
            <input type="file" accept=".md" multiple @change="onFileSelect" hidden />
          </label>
          <p class="upload-hint">仅支持 .md 格式</p>
        </div>

        <!-- Upload progress -->
        <div v-if="uploading" class="upload-progress">
          <div class="progress-text">上传中...</div>
        </div>

        <!-- Upload result -->
        <div v-if="uploadResult" class="upload-result" :class="uploadResult.type">
          {{ uploadResult.text }}
        </div>
      </section>

      <!-- Document list -->
      <section class="doc-list-section">
        <div class="list-header">
          <h2 class="list-title">文档列表</h2>
          <button class="refresh-btn" @click="loadDocuments" :disabled="loading">
            {{ loading ? '加载中...' : '🔄 刷新' }}
          </button>
        </div>

        <div v-if="loading" class="placeholder">加载中...</div>
        <div v-else-if="documents.length === 0" class="placeholder">暂无文档，请上传 Markdown 文件。</div>

        <div v-else class="doc-list">
          <div v-for="doc in documents" :key="doc.docId" class="doc-item" :class="statusClass(doc.status)">
            <div class="doc-info">
              <div class="doc-name">{{ doc.fileName }}</div>
              <div class="doc-meta">
                <span class="doc-size">{{ formatSize(doc.fileSize) }}</span>
                <span class="doc-status" :class="statusClass(doc.status)">{{ statusLabel(doc.status) }}</span>
              </div>
              <div class="doc-time">上传于 {{ formatTime(doc.uploadedAt) }}</div>
              <div v-if="doc.failReason" class="doc-error">{{ doc.failReason }}</div>
            </div>
            <div class="doc-actions">
              <button
                v-if="doc.status === 'FAILED_RETRYABLE'"
                class="retry-btn"
                @click="retryDocument(doc.docId)"
              >🔄 重试</button>
              <button
                v-if="doc.status !== 'DELETED'"
                class="delete-btn"
                @click="confirmDelete(doc)"
              >🗑</button>
            </div>
          </div>
        </div>
      </section>
    </div>

    <!-- Delete confirmation modal -->
    <div v-if="deleteTarget" class="modal-overlay" @click.self="deleteTarget = null">
      <div class="modal">
        <p class="modal-text">确认删除文档「{{ deleteTarget.fileName }}」？</p>
        <p class="modal-hint">删除后 RAG 将不再引用该文档内容。</p>
        <div class="modal-actions">
          <button class="cancel-btn" @click="deleteTarget = null">取消</button>
          <button class="confirm-delete-btn" @click="doDelete">确认删除</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const API_BASE = process.env.NODE_ENV === 'production' ? '/api' : 'http://localhost:8123/api'

const documents = ref([])
const loading = ref(false)
const uploading = ref(false)
const isDragging = ref(false)
const uploadResult = ref(null)
const deleteTarget = ref(null)

const goBack = () => router.push('/')

const loadDocuments = async () => {
  loading.value = true
  try {
    const token = localStorage.getItem('token')
    const res = await fetch(`${API_BASE}/document/list`, {
      headers: { 'Authorization': `Bearer ${token}` }
    })
    const body = await res.json()
    documents.value = body.data || []
    // Sort: INDEXED first, then by upload time desc
    documents.value.sort((a, b) => {
      if (a.status === 'INDEXED' && b.status !== 'INDEXED') return -1
      if (b.status === 'INDEXED' && a.status !== 'INDEXED') return 1
      return 0
    })
  } catch (e) {
    console.error('加载文档列表失败', e)
  } finally {
    loading.value = false
  }
}

const uploadFile = async (file) => {
  if (!file.name.endsWith('.md')) {
    uploadResult.value = { type: 'error', text: `跳过非 Markdown 文件: ${file.name}` }
    return
  }
  uploading.value = true
  uploadResult.value = null
  try {
    const token = localStorage.getItem('token')
    const formData = new FormData()
    formData.append('file', file)
    const res = await fetch(`${API_BASE}/document/upload`, {
      method: 'POST',
      headers: { 'Authorization': `Bearer ${token}` },
      body: formData
    })
    const body = await res.json()
    if (body.code === 200) {
      uploadResult.value = { type: 'success', text: `✓ ${file.name} 上传成功` }
      await loadDocuments()
    } else {
      uploadResult.value = { type: 'error', text: body.message || '上传失败' }
    }
  } catch (e) {
    uploadResult.value = { type: 'error', text: '上传失败，请重试' }
  } finally {
    uploading.value = false
  }
}

const onFileSelect = (e) => {
  const files = e.target.files
  for (const file of files) uploadFile(file)
}

const onDrop = (e) => {
  isDragging.value = false
  const files = e.dataTransfer.files
  for (const file of files) uploadFile(file)
}

const confirmDelete = (doc) => {
  deleteTarget.value = doc
}

const doDelete = async () => {
  if (!deleteTarget.value) return
  try {
    const token = localStorage.getItem('token')
    await fetch(`${API_BASE}/document/${deleteTarget.value.docId}`, {
      method: 'DELETE',
      headers: { 'Authorization': `Bearer ${token}` }
    })
    deleteTarget.value = null
    await loadDocuments()
  } catch (e) {
    console.error('删除失败', e)
  }
}

const retryDocument = async (docId) => {
  // Re-upload the same file — dedup will handle it
  uploadResult.value = { type: 'info', text: '重试功能需要重新上传文件' }
}

const statusLabel = (status) => {
  const map = {
    UPLOADING: '上传中', PARSING: '解析中', EMBEDDING: '向量化中',
    INDEXING: '索引中', INDEXED: '已就绪', FAILED_RETRYABLE: '可重试',
    FAILED_FINAL: '失败', DELETED: '已删除'
  }
  return map[status] || status
}

const statusClass = (status) => {
  if (status === 'INDEXED') return 'indexed'
  if (status && status.startsWith('FAILED')) return 'failed'
  if (status === 'DELETED') return 'deleted'
  return 'processing'
}

const formatSize = (bytes) => {
  if (!bytes) return '—'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(1) + ' MB'
}

const formatTime = (value) => {
  if (!value) return '—'
  const d = new Date(value)
  if (isNaN(d.getTime())) return value
  return d.toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

onMounted(loadDocuments)
</script>

<style scoped>
.kb-layout {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #f0f2f5;
  overflow: hidden;
}

.header {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  padding: 14px 24px;
  background: #059669;
  color: white;
  box-shadow: 0 2px 8px rgba(0,0,0,0.15);
}
.back-button { cursor: pointer; font-size: 15px; opacity: 0.85; }
.back-button:hover { opacity: 1; }
.title { font-size: 18px; font-weight: bold; margin: 0; text-align: center; }
.doc-count { justify-self: end; font-size: 13px; opacity: 0.8; }

.content { flex: 1; overflow-y: auto; padding: 20px; max-width: 800px; margin: 0 auto; width: 100%; }

/* Upload */
.upload-section { margin-bottom: 24px; }
.upload-zone {
  border: 2px dashed #d1d5db;
  border-radius: 12px;
  padding: 32px;
  text-align: center;
  transition: all 0.2s;
  background: white;
}
.upload-zone.dragging { border-color: #059669; background: rgba(5,150,105,0.05); }
.upload-icon { font-size: 32px; margin-bottom: 8px; }
.upload-text { color: #6b7280; font-size: 14px; margin-bottom: 8px; }
.upload-btn {
  display: inline-block;
  background: #059669;
  color: white;
  padding: 8px 20px;
  border-radius: 8px;
  cursor: pointer;
  font-size: 14px;
  transition: background 0.2s;
}
.upload-btn:hover { background: #047857; }
.upload-hint { color: #9ca3af; font-size: 12px; margin-top: 8px; }
.upload-progress { text-align: center; padding: 12px; color: #059669; }
.upload-result { text-align: center; padding: 8px; border-radius: 8px; font-size: 13px; margin-top: 8px; }
.upload-result.success { background: rgba(5,150,105,0.1); color: #059669; }
.upload-result.error { background: rgba(239,68,68,0.1); color: #dc2626; }

/* Document list */
.doc-list-section { background: white; border-radius: 12px; overflow: hidden; box-shadow: 0 1px 4px rgba(0,0,0,0.08); }
.list-header { display: flex; justify-content: space-between; align-items: center; padding: 16px 20px; border-bottom: 1px solid #e5e7eb; }
.list-title { font-size: 16px; font-weight: 600; margin: 0; }
.refresh-btn { background: #f3f4f6; border: none; border-radius: 8px; padding: 6px 14px; font-size: 13px; cursor: pointer; }
.refresh-btn:hover { background: #e5e7eb; }
.refresh-btn:disabled { opacity: 0.5; cursor: not-allowed; }

.placeholder { text-align: center; color: #6b7280; font-size: 14px; padding: 40px; }

.doc-list { padding: 12px 20px; }
.doc-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 0;
  border-bottom: 1px solid #f3f4f6;
}
.doc-item:last-child { border-bottom: none; }
.doc-info { flex: 1; }
.doc-name { font-size: 14px; font-weight: 500; color: #1f2937; }
.doc-meta { display: flex; gap: 8px; margin-top: 4px; }
.doc-size { font-size: 12px; color: #9ca3af; }
.doc-status {
  font-size: 11px; padding: 1px 8px; border-radius: 10px; font-weight: 500;
}
.doc-status.indexed { background: rgba(16,185,129,0.12); color: #059669; }
.doc-status.processing { background: rgba(245,158,11,0.12); color: #d97706; }
.doc-status.failed { background: rgba(239,68,68,0.12); color: #dc2626; }
.doc-status.deleted { background: rgba(107,114,128,0.12); color: #6b7280; }
.doc-time { font-size: 11px; color: #9ca3af; margin-top: 2px; }
.doc-error { font-size: 12px; color: #dc2626; margin-top: 4px; }
.doc-actions { display: flex; gap: 6px; }
.retry-btn {
  background: #fef3c7; border: none; border-radius: 6px; padding: 4px 10px;
  font-size: 12px; cursor: pointer; color: #92400e;
}
.retry-btn:hover { background: #fde68a; }
.delete-btn {
  background: none; border: none; cursor: pointer; font-size: 14px;
  opacity: 0.5; transition: opacity 0.2s;
}
.delete-btn:hover { opacity: 1; }

/* Modal */
.modal-overlay {
  position: fixed; inset: 0; background: rgba(0,0,0,0.4);
  display: flex; align-items: center; justify-content: center; z-index: 1000;
}
.modal {
  background: white; border-radius: 12px; padding: 24px; max-width: 400px; width: 90%;
}
.modal-text { font-size: 15px; font-weight: 500; margin-bottom: 8px; }
.modal-hint { font-size: 13px; color: #6b7280; margin-bottom: 16px; }
.modal-actions { display: flex; justify-content: flex-end; gap: 8px; }
.cancel-btn {
  background: #f3f4f6; border: none; border-radius: 8px; padding: 8px 16px;
  font-size: 13px; cursor: pointer;
}
.confirm-delete-btn {
  background: #dc2626; color: white; border: none; border-radius: 8px; padding: 8px 16px;
  font-size: 13px; cursor: pointer;
}
.confirm-delete-btn:hover { background: #b91c1c; }
</style>
