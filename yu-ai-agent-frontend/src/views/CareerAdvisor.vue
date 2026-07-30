<template>
  <div class="chat-layout">
    <!-- Sidebar -->
    <aside class="sidebar" :class="{ collapsed: sidebarCollapsed }">
      <div class="side-head">
        <h2 v-if="!sidebarCollapsed">对话</h2>
        <button class="tb-btn" @click="createNewSession" title="新对话">+</button>
        <button class="collapse-toggle" @click="sidebarCollapsed = !sidebarCollapsed">
          {{ sidebarCollapsed ? '›' : '‹' }}
        </button>
      </div>

      <template v-if="!sidebarCollapsed">
        <div class="side-search">
          <WpIcon class="s-icon" name="search" :size="14" />
          <input v-model="searchKeyword" placeholder="搜索对话…" @input="onSearchInput" />
          <button v-if="searchKeyword" class="search-clear" @click="clearSearch">×</button>
        </div>

        <!-- Search results -->
        <div v-if="searchMode" class="conv-list">
          <div v-if="isSearching" class="search-status">搜索中...</div>
          <div v-else-if="searchResults.length === 0" class="search-status">无匹配结果</div>
          <div v-else v-for="result in searchResults" :key="result.chatId"
            class="conv" @click="switchSession(result.chatId); clearSearch()">
            <div class="c-title">{{ result.title }}</div>
            <div class="c-meta"><span>{{ result.relevance }}%</span></div>
          </div>
        </div>

        <!-- Session list with time groups -->
        <div v-if="!searchMode" class="conv-list">
          <template v-for="group in sessionGroups" :key="group.label">
            <div class="conv-group-label">{{ group.label }}</div>
            <div v-for="session in group.sessions" :key="session.chatId"
              class="conv" :class="{ on: session.chatId === currentChatId }"
              @click="switchSession(session.chatId)">
              <div class="c-title">
                <span v-if="!session.editing">{{ session.title }}</span>
                <input v-else v-model="session.newTitle" class="rename-input"
                  @blur="saveRename(session)" @keyup.enter="saveRename(session)"
                  @keyup.escape="session.editing = false" @click.stop />
              </div>
              <div class="c-meta">
                <span>{{ formatTimeAgo(session.createTime) }}</span>
                <div class="c-actions" @click.stop>
                  <button class="c-act" @click="startRename(session)" title="重命名"><WpIcon name="edit" :size="13" /></button>
                  <button class="c-act" @click="handleArchive(session.chatId)" title="归档"><WpIcon name="archive" :size="13" /></button>
                  <button class="c-act" @click="removeSession(session.chatId)" title="删除">×</button>
                </div>
              </div>
            </div>
          </template>
          <div v-if="sessions.length === 0" class="empty-conv">暂无历史对话</div>
        </div>

        <!-- Undo delete toast -->
        <Transition name="toast">
          <div v-if="undoToast" class="undo-toast">
            <span>已移入回收站</span>
            <button @click="handleUndoDelete">撤销</button>
          </div>
        </Transition>

        <!-- Archived -->
        <div class="archived-section">
          <button class="archived-toggle" @click="toggleArchived">
            {{ showArchived ? '▼' : '▶' }} 归档会话
          </button>
          <div v-if="showArchived" class="conv-list archived-list">
            <div v-for="session in archivedSessions" :key="session.chatId" class="conv archived">
              <div class="c-title">{{ session.title }}</div>
              <div class="c-meta">
                <div class="c-actions" @click.stop>
                  <button class="c-act" @click="handleUnarchive(session.chatId)">📤</button>
                  <button class="c-act" @click="removeSession(session.chatId)">×</button>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Always-visible AI team entry -->
        <div class="side-team">
          <div class="side-team-label">我的 AI 团队</div>
          <button class="side-team-item" type="button" @click="showCompanionDrawer = true">
            <span class="side-team-dot companion"></span>
            <span class="side-team-text">
              <strong>{{ companionForm.displayName || '职场伙伴' }}</strong>
              <small>点击调整风格</small>
            </span>
          </button>
          <button class="side-team-item" type="button" @click="openEmployeePanel">
            <span class="side-team-dot employee"></span>
            <span class="side-team-text">
              <strong>数字员工</strong>
              <small>{{ activeEmployeeLabel }}</small>
            </span>
          </button>
          <button class="side-team-item" type="button" @click="openPackPanel">
            <span class="side-team-dot companion"></span>
            <span class="side-team-text">
              <strong>专家包</strong>
              <small>{{ enabledPackLabel }}</small>
            </span>
          </button>
          <button class="side-team-item" type="button" @click="openTaskPanel">
            <span class="side-team-dot employee"></span>
            <span class="side-team-text">
              <strong>任务中心</strong>
              <small>{{ myTasks.length ? myTasks.length + ' 项' : '暂无待办' }}</small>
            </span>
          </button>
        </div>
      </template>
    </aside>

    <!-- Chat core -->
    <div class="chat-core">
      <div class="chat-top">
        <button class="back-mobile" @click="$router.push('/')">←</button>
        <div class="chat-top-info">
          <h3>{{ currentSessionTitle }}</h3>
          <div class="agent-tag">{{ currentAgent.name }}</div>
        </div>
        <div class="chat-top-actions">
          <button class="top-pill companion" type="button" @click="showCompanionDrawer = true" title="个人伙伴设置">
            {{ companionForm.displayName || '我的伙伴' }}
          </button>
          <button class="top-pill employee" type="button" @click="openEmployeePanel" title="数字员工">
            {{ activeEmployeeShort }}
          </button>
          <button class="tb-btn" @click="openProfile" title="画像"><WpIcon name="user" :size="16" /></button>
          <div class="more-menu-wrap">
            <button class="tb-btn" @click="showMoreMenu = !showMoreMenu" title="更多功能">⋯</button>
            <div v-if="showMoreMenu" class="more-menu" @click="showMoreMenu = false">
              <div class="menu-item" @click="openProfile"><WpIcon name="user" :size="15" /> 我的画像</div>
              <div class="menu-item" @click="$router.push('/favorites')"><WpIcon name="star" :size="15" /> 我的收藏</div>
              <div class="menu-item" @click="$router.push('/usage')"><WpIcon name="usage" :size="15" /> 使用统计</div>
              <div class="menu-item" @click="showCompanionDrawer = true"><WpIcon name="agent" :size="15" /> 伙伴设置</div>
              <div class="menu-item" @click="openEmployeePanel"><WpIcon name="agent" :size="15" /> 我的数字员工</div>
              <div class="menu-item" @click="openPackPanel"><WpIcon name="agent" :size="15" /> 专家包</div>
              <div class="menu-item" @click="openTaskPanel"><WpIcon name="usage" :size="15" /> 任务中心</div>
              <div class="menu-item" @click="openRightPanel('progress')"><WpIcon name="artifact" :size="15" /> 执行进度</div>
              <div class="menu-item" @click="$router.push('/artifacts')"><WpIcon name="artifact" :size="15" /> 交付物</div>
              <div class="menu-item" @click="$router.push('/knowledge')"><WpIcon name="knowledge" :size="15" /> 知识库</div>
              <div class="menu-item" @click="$router.push('/chat/super')"><WpIcon name="agent" :size="15" /> 超级智能体</div>
            </div>
          </div>
        </div>
      </div>

      <!-- Messages -->
      <div class="msgs" ref="messagesContainer">
        <!-- AI Presence (welcome) -->
        <div v-if="messages.length === 0 && !isThinking && !isLoadingHistory" class="presence">
          <div class="ai-orb"></div>
          <div class="presence-body">
            <div class="presence-text">
              你好，我是 <b>{{ companionForm.displayName || '你的职场伙伴' }}</b>。<br><br>
              以后想让我做什么，直接说就行——改简历、谈涨薪、规划离职，都能搞定。<br>
              也可以招几个 <span class="hl">数字员工</span>，专人专事。
              <div class="presence-privacy"><WpIcon name="lock" :size="12" /> 对话仅存储在本地，不会分享给第三方</div>

              <div class="capability-cards">
                <button class="cap-card" type="button" @click="showCompanionDrawer = true">
                  <span class="cap-card-kicker">个人伙伴</span>
                  <strong>{{ companionForm.displayName || '职场伙伴' }}</strong>
                  <p>改语气、关注方向、人设——让我更懂你</p>
                  <span class="cap-card-cta">去设置 →</span>
                </button>
                <button class="cap-card" type="button" @click="openEmployeePanel">
                  <span class="cap-card-kicker">数字员工</span>
                  <strong>{{ myEmployees.length ? `已有 ${myEmployees.length} 位` : '还没有员工' }}</strong>
                  <p>从模板秒建简历专员、谈薪顾问等专精 Agent</p>
                  <span class="cap-card-cta">{{ myEmployees.length ? '去管理 →' : '去创建 →' }}</span>
                </button>
              </div>

              <div class="suggest-chips">
                <button
                  v-for="chip in coldStartChips"
                  :key="chip.id"
                  class="suggest-chip"
                  type="button"
                  @click="sendSuggested(chip.message)"
                >{{ chip.label }}</button>
              </div>
            </div>
          </div>
        </div>

        <!-- Message rows -->
        <div v-for="(msg, index) in messages" :key="index" class="msg-wrapper"
          @mouseenter="hoveredMsg = index" @mouseleave="hoveredMsg = -1">
          <!-- Routing badge with explanation -->
          <div v-if="msg.type === 'routing'" class="routing-badge">
            <span class="routing-text">{{ msg.content }}</span>
            <span class="routing-hint" v-if="msg.content.includes('路由到')">判断不对？直接告诉我就好</span>
          </div>
          <!-- Clarification -->
          <div v-else-if="msg.type === 'clarification'" class="msg-row ai">
            <div class="msg-av">❓</div>
            <div class="msg-bub" v-html="renderMarkdown(msg.content)"></div>
          </div>
          <!-- AI message -->
          <div v-else-if="!msg.isUser" class="msg-row ai" :class="'agent-' + (msg.agentType || 'GENERAL').toLowerCase()">
            <div class="msg-av">{{ getAgentAvatar(msg.agentType) }}</div>
            <div>
              <div v-if="msg.agentName" class="agent-label">{{ msg.agentName }}</div>
              <div class="msg-bub" v-html="renderMarkdown(msg.content)"></div>
              <div v-if="msg.hitlApprovalId && !msg.hitlResolved" class="hitl-card">
                <button class="hitl-btn ok" :disabled="isStreaming || msg.hitlBusy" @click="handleHitlApprove(msg)">确认创建</button>
                <button class="hitl-btn no" :disabled="isStreaming || msg.hitlBusy" @click="handleHitlReject(msg)">取消</button>
              </div>
              <button v-if="msg.type === 'error'" class="retry-btn" @click="retrySendMessage">
                🔄 重试
              </button>
              <div class="msg-actions" v-if="hoveredMsg === index || msg.feedbackRating">
                <button class="msg-act" @click="copyMessage(msg.content)" title="复制">📋</button>
                <button class="msg-act" @click="toggleFavorite(msg, index)">
                  {{ msg.favorited ? '⭐' : '☆' }}
                </button>
                <button
                  class="msg-act"
                  :class="{ on: msg.feedbackRating === 'UP' }"
                  :disabled="!!msg.feedbackRating"
                  title="有帮助"
                  @click="rateMessage(msg, 'UP')"
                >👍</button>
                <button
                  class="msg-act"
                  :class="{ on: msg.feedbackRating === 'DOWN' }"
                  :disabled="!!msg.feedbackRating"
                  title="需改进"
                  @click="rateMessage(msg, 'DOWN')"
                >👎</button>
              </div>
              <div v-if="msg.artifactId" class="artifact-chip-row">
                <button class="artifact-chip" type="button" @click="openArtifact(msg.artifactId)">📄 查看交付物</button>
              </div>
              <div v-if="msg.suggestedActions?.length" class="suggest-chips under-msg">
                <button
                  v-for="chip in msg.suggestedActions"
                  :key="chip.id"
                  class="suggest-chip"
                  type="button"
                  :disabled="isStreaming"
                  @click="sendSuggested(chip.message)"
                >{{ chip.label }}</button>
              </div>
            </div>
          </div>
          <!-- User message -->
          <div v-else class="msg-row you">
            <div class="msg-bub">{{ msg.content }}</div>
            <div class="msg-actions" v-if="hoveredMsg === index">
              <button class="msg-act" @click="toggleFavorite(msg, index)">
                {{ msg.favorited ? '⭐' : '☆' }}
              </button>
            </div>
          </div>
        </div>

        <!-- Thinking indicator: friendly phase copy (tech detail lives in right panel) -->
        <div v-if="isThinking" class="typing" role="status" aria-live="polite">
          <div class="msg-av thinking-av">✦</div>
          <div class="typing-bubble">
            <div class="typing-dots"><b></b><b></b><b></b></div>
            <span class="typing-label">{{ thinkingLabel }}</span>
          </div>
        </div>

        <!-- Skeleton loading -->
        <template v-if="isLoadingHistory">
          <div v-for="i in 3" :key="'skel-'+i" class="skeleton-msg" :class="i % 2 === 0 ? 'skel-right' : ''">
            <div class="skel-av"></div>
            <div class="skel-bub"><div class="skel-line" :style="{ width: (50 + i*12) + '%' }"></div><div class="skel-line short"></div></div>
          </div>
        </template>
      </div>

      <!-- Quality blocked -->
      <div v-if="qualityBlocked" class="quality-blocked">
        <span>🚫 {{ qualityBlocked }}</span>
      </div>

      <!-- Quality review (user-friendly) -->
      <div v-if="qualityReview && !qualityBlocked" class="quality-strip" :class="'risk-' + (qualityReview.riskLevel || '').toLowerCase()">
        <span class="quality-icon">{{ qualityIcon }}</span>
        <span class="quality-text">{{ qualityText }}</span>
        <button class="quality-detail-btn" @click="showQualityDetail = !showQualityDetail">详情</button>
        <div v-if="showQualityDetail" class="quality-detail-panel">
          <div class="qd-row"><span class="qd-label">模式</span><span>{{ qualityReview.mode || 'REVIEW' }}</span></div>
          <div class="qd-row"><span class="qd-label">准确性</span><span class="qd-bar"><span class="qd-fill" :style="{ width: qualityReview.accuracyScore + '%' }"></span></span><span>{{ qualityReview.accuracyScore }}%</span></div>
          <div class="qd-row"><span class="qd-label">完整性</span><span class="qd-bar"><span class="qd-fill" :style="{ width: qualityReview.completenessScore + '%' }"></span></span><span>{{ qualityReview.completenessScore }}%</span></div>
          <div class="qd-row"><span class="qd-label">逻辑性</span><span class="qd-bar"><span class="qd-fill" :style="{ width: qualityReview.logicScore + '%' }"></span></span><span>{{ qualityReview.logicScore }}%</span></div>
          <div class="qd-row"><span class="qd-label">幻觉风险</span><span class="qd-bar"><span class="qd-fill warn" :style="{ width: qualityReview.hallucinationScore + '%' }"></span></span><span>{{ qualityReview.hallucinationScore }}%</span></div>
          <div v-if="qualityReview.issues?.length" class="qd-issues">
            <span class="qd-label">发现问题</span>
            <ul><li v-for="(issue, i) in qualityReview.issues" :key="i">{{ issue }}</li></ul>
          </div>
          <div v-if="qualityReview.suggestions?.length" class="qd-suggestions">
            <span class="qd-label">改进建议</span>
            <ul><li v-for="(sug, i) in qualityReview.suggestions" :key="i">{{ sug }}</li></ul>
          </div>
        </div>
      </div>

      <!-- Chat input bar -->
      <div class="chat-bar">
        <div class="team-strip">
          <button class="team-strip-btn" type="button" @click="showCompanionDrawer = true">
            伙伴 · {{ companionForm.displayName || '职场伙伴' }}
          </button>
          <button class="team-strip-btn accent" type="button" @click="openEmployeePanel">
            数字员工 · {{ activeEmployeeShort }}
          </button>
          <button
            v-if="!myEmployees.length"
            class="team-strip-btn ghost"
            type="button"
            @click="openEmployeePanel"
          >+ 招一个</button>
        </div>
        <div v-if="attachedFile" class="attached-file">
          <span class="attached-name">📎 {{ attachedFile.name }}</span>
          <select v-model="attachHint" class="attach-hint" :disabled="isStreaming" title="文档类型提示">
            <option value="resume">简历</option>
            <option value="offer">Offer/薪资</option>
          </select>
          <button type="button" @click="clearAttachedFile" :disabled="isStreaming" title="移除">×</button>
        </div>
        <div v-if="perceptionBusy" class="perception-status">正在感知预处理文档…</div>
        <div v-else-if="lastPerceptionMeta" class="perception-status ok">
          感知完成 · 置信度 {{ (lastPerceptionMeta.confidence * 100).toFixed(0) }}%
          <span v-if="lastPerceptionMeta.injectionRisk" class="perception-risk">已净化注入风险</span>
          <span v-if="lastPerceptionMeta.fieldCount" class="perception-fields">· {{ lastPerceptionMeta.fieldCount }} 个字段</span>
        </div>
        <div class="chat-bar-wrap" :class="{ focused: barFocused }">
          <div v-if="slashOpen" class="slash-menu">
            <button
              v-for="cmd in filteredSlashCommands"
              :key="cmd.id"
              type="button"
              class="slash-item"
              @mousedown.prevent="applySlashCommand(cmd)"
            >
              <strong>{{ cmd.label }}</strong>
              <small>{{ cmd.message }}</small>
            </button>
            <div v-if="!filteredSlashCommands.length" class="slash-empty">无匹配命令</div>
          </div>
          <textarea
            v-model="inputMessage"
            @keydown.enter.exact.prevent="sendMessage"
            @keydown.shift.enter="inputMessage += '\n'"
            @keydown.escape="slashOpen = false"
            @input="onInputChange"
            @focus="barFocused = true"
            @blur="barFocused = false; slashOpen = false"
            placeholder="继续聊… 输入 / 唤起快捷命令"
            :disabled="isStreaming"
            rows="1"
          ></textarea>
          <input ref="fileInput" type="file" accept=".pdf,.docx,.txt,.md,.csv,.png,.jpg,.jpeg,.webp,.gif" style="display:none" @change="onFileSelected" />
          <button class="bar-btn" @click="$refs.fileInput.click()" :disabled="isStreaming" title="上传简历/Offer（感知预处理）">📎</button>
          <button v-if="speechSupported" class="bar-btn" :class="{ listening: isListening }" @click="toggleVoice" :disabled="isStreaming">{{ isListening ? '⏹' : '🎤' }}</button>
          <button v-if="isStreaming" class="chat-stop" @click="stopGeneration" title="停止生成">⏹</button>
          <button v-else class="chat-send" @click="sendMessage" :disabled="!inputMessage.trim() && !attachedFile">→</button>
        </div>
      </div>
    </div>

    <!-- Right workbench: artifact + progress -->
    <div class="panel" :class="{ show: rightPanelOpen }">
      <div class="panel-in">
        <div class="panel-head">
          <div class="panel-tabs">
            <button type="button" class="panel-tab" :class="{ on: rightPanelTab === 'artifact' }" @click="rightPanelTab = 'artifact'">交付物</button>
            <button type="button" class="panel-tab" :class="{ on: rightPanelTab === 'progress' }" @click="rightPanelTab = 'progress'">执行进度</button>
          </div>
          <button class="tb-btn" @click="closeRightPanel">×</button>
        </div>
        <div v-if="rightPanelTab === 'artifact'" class="panel-content">
          <div v-if="latestArtifact" class="artifact-meta">
            <span class="artifact-type">{{ latestArtifact.type || 'ARTIFACT' }}</span>
            <h3>{{ latestArtifact.title || '交付物' }}</h3>
          </div>
          <div v-if="latestArtifact" v-html="renderMarkdown(latestArtifact.content || '等待生成...')"></div>
          <div v-else class="panel-empty">本轮暂无交付物。多专家协作或换人后会自动出现在这里。</div>
          <div v-if="sessionArtifacts.length" class="artifact-list">
            <div class="artifact-list-title">本会话产物</div>
            <button
              v-for="a in sessionArtifacts"
              :key="a.artifactId"
              type="button"
              class="artifact-list-item"
              @click="openArtifact(a.artifactId)"
            >
              <span>{{ a.title || a.type || '未命名交付物' }}</span>
              <span class="artifact-usage-group">
                <span v-if="a.offeredCount > 0" class="artifact-usage">
                  已推荐 {{ a.offeredCount }}
                </span>
                <span v-if="a.adoptedCount > 0" class="artifact-usage adopted">
                  已采用 {{ a.adoptedCount }}
                </span>
              </span>
            </button>
          </div>
        </div>
        <div v-else class="panel-content progress-panel">
          <div v-if="sandboxPolicyLabel" class="sandbox-badge">{{ sandboxPolicyLabel }}</div>
          <div v-if="agentProgressLog.length" class="progress-log">
            <div v-for="(p, i) in agentProgressLog" :key="i" class="progress-item" :class="p.status">
              <span class="p-agent">{{ p.agent }}</span>
              <span class="p-status">{{ p.status }}</span>
              <span v-if="p.durationMs != null" class="p-dur">{{ p.durationMs }}ms</span>
            </div>
          </div>
          <TraceTimelineView v-if="traceSteps.length" :trace="{ status: traceStatus || 'RUNNING', spans: traceSteps }" />
          <div v-else class="panel-empty">发送消息后，专家进度与 Trace 会显示在这里。</div>
          <button
            v-if="lastTraceId"
            type="button"
            class="save-skill-btn"
            :disabled="skillDraftBusy"
            @click="saveSkillFromLastTrace"
          >{{ skillDraftBusy ? '生成中…' : '保存为技能' }}</button>
        </div>
      </div>
    </div>

    <!-- Profile overlay -->
    <div v-if="profileVisible" class="overlay" @click.self="closeProfile">
      <div class="overlay-panel">
        <div class="overlay-header">
          <h2><WpIcon name="user" :size="18" /> 我的画像</h2>
          <button class="overlay-close" type="button" aria-label="关闭" @click="closeProfile">
            <span aria-hidden="true">×</span>
          </button>
        </div>
        <div class="overlay-body">
          <div v-if="profileLoading" class="overlay-loading">画像加载中...</div>
          <div v-else-if="profileError" class="overlay-empty">{{ profileError }}</div>
          <div v-else-if="!profile" class="overlay-empty">暂无画像。多与 AI 对话后会自动构建。</div>
          <div v-else class="profile-content">
            <div class="profile-field"><span class="field-label">沟通偏好</span><span class="field-value">{{ communicationPreferenceText }}</span></div>
            <div class="profile-field"><span class="field-label">语气偏好</span><span class="field-value">{{ profile.tonePreference || '暂无' }}</span></div>
            <div class="profile-field">
              <span class="field-label">关注领域</span>
              <div class="field-tags">
                <span v-if="profile.focusAreas?.length" v-for="(a, i) in profile.focusAreas" :key="i" class="field-tag">{{ a }}</span>
                <span v-else class="field-empty">暂无</span>
              </div>
            </div>
            <div class="profile-field">
              <span class="field-label">核心诉求</span>
              <div class="field-tags">
                <span v-if="profile.coreNeeds?.length" v-for="(n, i) in profile.coreNeeds" :key="i" class="field-tag">{{ n }}</span>
                <span v-else class="field-empty">暂无</span>
              </div>
            </div>
          </div>
        </div>
        <div class="overlay-footer">
          <button class="clear-btn" @click="handleClearProfile" :disabled="clearing">{{ clearing ? '清空中...' : '清空画像' }}</button>
        </div>
      </div>
    </div>

    <!-- Companion settings -->
    <div v-if="showCompanionDrawer" class="overlay" @click.self="showCompanionDrawer = false">
      <div class="overlay-panel team-panel">
        <div class="overlay-header">
          <div class="overlay-title-block">
            <span class="overlay-badge companion">个人伙伴</span>
            <h2>调整你的职场伙伴</h2>
            <p>改称呼与风格后，下一轮对话就会按新规则回应。</p>
          </div>
          <button class="overlay-close" type="button" aria-label="关闭" @click="showCompanionDrawer = false">
            <span aria-hidden="true">×</span>
          </button>
        </div>
        <div class="overlay-body">
          <div class="team-form">
            <label class="team-field">
              <span class="field-label">称呼</span>
              <input v-model="companionForm.displayName" class="team-input" placeholder="你的职场伙伴" />
            </label>
            <label class="team-field">
              <span class="field-label">语气</span>
              <input v-model="companionForm.tone" class="team-input" placeholder="简洁、直接、少客套" />
            </label>
            <label class="team-field">
              <span class="field-label">关注方向</span>
              <input v-model="companionForm.focus" class="team-input" placeholder="简历、谈薪、职业方向" />
            </label>
            <label class="team-field">
              <span class="field-label">人设补充</span>
              <textarea v-model="companionForm.personaPrompt" rows="4" class="team-input team-textarea" placeholder="例如：回答先给结论，再给步骤；不确定时先问我"></textarea>
            </label>
          </div>
          <div v-if="companionMsg" class="team-toast ok">{{ companionMsg }}</div>
        </div>
        <div class="overlay-footer team-footer">
          <button class="btn-ghost" type="button" @click="showCompanionDrawer = false">取消</button>
          <button class="btn-primary" type="button" @click="saveCompanion" :disabled="companionSaving">
            {{ companionSaving ? '保存中...' : '保存并进化' }}
          </button>
        </div>
      </div>
    </div>

    <!-- Digital employees -->
    <div v-if="showEmployeePanel" class="overlay" @click.self="closeEmployeePanel">
      <div class="overlay-panel team-panel wide">
        <div class="overlay-header">
          <div class="overlay-title-block">
            <span class="overlay-badge employee">数字员工</span>
            <h2>{{ editingEmployee ? '自定义人设' : '我的数字员工' }}</h2>
            <p>{{ editingEmployee ? '保存后生成新版本，不满意可回滚。' : '从模板创建，再改成你喜欢的专精风格。' }}</p>
          </div>
          <button class="overlay-close" type="button" aria-label="关闭" @click="closeEmployeePanel">
            <span aria-hidden="true">×</span>
          </button>
        </div>
        <div class="overlay-body">
          <div v-if="editingEmployee" class="de-editor">
            <div class="team-form">
              <label class="team-field">
                <span class="field-label">员工名称</span>
                <input v-model="employeeEditForm.name" class="team-input" placeholder="例如：简历专员·量化版" />
              </label>
              <label class="team-field">
                <span class="field-label">人设与规则</span>
                <textarea
                  v-model="employeeEditForm.persona"
                  rows="7"
                  class="team-input team-textarea de-persona"
                  placeholder="例如：你是简历优化专员。先给结论，再给可执行修改点；优先量化成果；语气简洁、少客套。"
                ></textarea>
              </label>
            </div>
            <div class="de-preset-row">
              <span class="field-label">快速套用风格</span>
              <div class="suggest-chips">
                <button
                  v-for="preset in personaPresets"
                  :key="preset.id"
                  class="suggest-chip ghost"
                  type="button"
                  @click="applyPersonaPreset(preset)"
                >{{ preset.label }}</button>
              </div>
            </div>
            <div v-if="employeeMsg" class="team-toast" :class="employeeMsg.includes('失败') ? 'err' : 'ok'">{{ employeeMsg }}</div>
            <div class="de-editor-actions">
              <button class="btn-ghost" type="button" :disabled="employeeBusy" @click="cancelEmployeeEdit">返回列表</button>
              <button class="btn-primary" type="button" :disabled="employeeBusy" @click="saveEmployeeEdit">
                {{ employeeBusy ? '保存中...' : '保存人设' }}
              </button>
            </div>
          </div>

          <template v-else>
            <div class="de-section-title">从模板创建</div>
            <div class="tpl-grid">
              <button
                v-for="tpl in employeeTemplates"
                :key="tpl.agentCode"
                class="tpl-card"
                type="button"
                :disabled="employeeBusy"
                @click="createFromTemplate(tpl)"
              >
                <strong>{{ tpl.displayName }}</strong>
                <span>{{ tpl.description || '创建后可自定义人设' }}</span>
              </button>
            </div>
            <div class="de-section-title" style="margin-top:20px">已创建</div>
            <div v-if="!myEmployees.length" class="overlay-empty">还没有数字员工。选一个模板创建，然后点「自定义」写成你的风格。</div>
            <div v-for="emp in myEmployees" :key="emp.id" class="de-card" :class="{ active: emp.active }">
              <div class="de-card-head">
                <div>
                  <strong>{{ emp.name }}</strong>
                  <span v-if="emp.active" class="de-current">当前</span>
                </div>
                <span class="de-meta">v{{ emp.configVersion }}</span>
              </div>
              <p class="de-persona-preview">{{ emp.persona || '暂无人设，点「自定义」写上你的规则' }}</p>
              <div class="de-card-actions">
                <button class="btn-mini primary" type="button" @click="activateEmployee(emp)">设为当前</button>
                <button class="btn-mini" type="button" @click="startEmployeeEdit(emp)">自定义</button>
                <button class="btn-mini ghost" type="button" @click="sendSuggested(`用「${emp.name}」帮我处理：`); showEmployeePanel = false">试用</button>
                <button
                  v-if="emp.configVersion > 1"
                  class="btn-mini ghost"
                  type="button"
                  @click="rollbackEmployee(emp)"
                >回滚</button>
              </div>
            </div>
            <div v-if="employeeMsg" class="team-toast" :class="employeeMsg.includes('失败') ? 'err' : 'ok'">{{ employeeMsg }}</div>
          </template>
        </div>
      </div>
    </div>

    <!-- Expert packs -->
    <div v-if="showPackPanel" class="overlay" @click.self="showPackPanel = false">
      <div class="overlay-panel">
        <div class="overlay-header">
          <h2>专家包</h2>
          <button class="overlay-close" type="button" @click="showPackPanel = false">×</button>
        </div>
        <div class="overlay-body">
          <div v-if="!expertPacks.length" class="overlay-empty">暂无专家包</div>
          <div v-for="pack in expertPacks" :key="pack.packId" class="pack-row">
            <div>
              <strong>{{ pack.displayName || pack.packId }}</strong>
              <small>{{ (pack.skillNames || []).join(' · ') || '无绑定技能' }}</small>
            </div>
            <button class="btn-mini" type="button" @click="toggleExpertPack(pack)">
              {{ pack.enabled ? '已启用' : '启用' }}
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- Task center -->
    <div v-if="showTaskPanel" class="overlay" @click.self="showTaskPanel = false">
      <div class="overlay-panel">
        <div class="overlay-header">
          <h2>任务中心</h2>
          <button class="overlay-close" type="button" @click="showTaskPanel = false">×</button>
        </div>
        <div class="overlay-body">
          <div v-if="!myTasks.length" class="overlay-empty">暂无待确认或暂停中的任务</div>
          <div v-for="(t, i) in myTasks" :key="t.id || i" class="pack-row">
            <div>
              <strong>{{ t.title || t.type }}</strong>
              <small>{{ t.status }} · {{ t.detail || '' }}</small>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick, computed, shallowReactive } from 'vue'
import { useRouter } from 'vue-router'
import { useHead } from '@vueuse/head'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import TraceTimelineView from '../components/TraceTimelineView.vue'
import WpIcon from '../components/WpIcon.vue'
import { login, getMe, createSession, listSessions, deleteSession, chatWithOrchestrator, resumeOrchestratorChat, getMyProfile, clearMyProfile, getChatMessages, renameSession, archiveSession, listArchivedSessions, searchSessions, preprocessPerceptionAndBind, addFavorite, removeFavorite, unarchiveSession, submitFeedback, getMyCompanion, updateMyCompanion, listDigitalEmployeeTemplates, listMyDigitalEmployees, createDigitalEmployee, updateDigitalEmployee, rollbackDigitalEmployee, setActiveDigitalEmployee, listMyArtifacts, getMyArtifactDetail, listExpertPacks, setExpertPackEnabled, listSkills, draftSkillFromTrace, saveDraftSkill, listMyTasks, getSandboxPolicy } from '../api'

useHead({ title: '职场顾问 - WorkPilot' })

const router = useRouter()
const messagesContainer = ref(null)
const inputMessage = ref('')
const barFocused = ref(false)
const messages = ref([])
const lastUserMessage = ref('')
const sessions = ref([])
const currentChatId = ref('')
const isStreaming = ref(false)
const isThinking = ref(false)
const sidebarCollapsed = ref(false)
const currentAgent = ref({ name: '智能路由中', type: 'general' })
const hoveredMsg = ref(-1)
const showMoreMenu = ref(false)
const isLoadingHistory = ref(false)
const thinkingLabel = ref('正在分析…')
const undoToast = ref(null)
let undoTimer = null
let thinkingEscalateTimers = []

/** Map backend step labels → user-facing thinking phrases (ChatGPT/Claude style). */
const FRIENDLY_THINKING = [
  { match: /技能|skill/i, label: '正在找合适的方法…' },
  { match: /NLU|意图|理解/i, label: '正在理解你的问题…' },
  { match: /路由/i, label: '正在连接专家…' },
  { match: /画像/i, label: '正在回忆你的背景…' },
  { match: /交付物|产物|artifact/i, label: '正在查阅相关材料…' },
  { match: /记忆|memory/i, label: '正在整理对话上下文…' },
  { match: /执行|专家|Resume|Negotiation|Escape|顾问/i, label: '专家正在认真作答…' },
  { match: /质量|质检|审查/i, label: '正在检查回答质量…' },
  { match: /查询重写|QUERY_REWRITE/i, label: '正在优化检索词…' },
  { match: /检索|RAG/i, label: '正在查阅知识库…' },
]

const setThinkingPhase = (label, { resetEscalate = true } = {}) => {
  if (!label || !isThinking.value) return
  thinkingLabel.value = label
  if (resetEscalate) startThinkingEscalate()
}

const friendlyLabelFromSpan = (span) => {
  const text = `${span?.stepTypeDisplayName || ''} ${span?.label || ''} ${span?.stepType || ''}`
  // Skip noisy micro-steps that shouldn't surface as thinking copy
  if (/消费|CONSUME|SKIP/i.test(text)) return null
  for (const rule of FRIENDLY_THINKING) {
    if (rule.match.test(text)) return rule.label
  }
  return null
}

const clearThinkingEscalate = () => {
  thinkingEscalateTimers.forEach(clearTimeout)
  thinkingEscalateTimers = []
}

const startThinkingEscalate = () => {
  clearThinkingEscalate()
  // Escalate only if still waiting for first token (Perplexity/Claude pattern)
  thinkingEscalateTimers.push(setTimeout(() => {
    if (isThinking.value) setThinkingPhase('还在准备中，稍等一下…', { resetEscalate: false })
  }, 2500))
  thinkingEscalateTimers.push(setTimeout(() => {
    if (isThinking.value) setThinkingPhase('专家在查阅资料，可能需要多等一会儿…', { resetEscalate: false })
  }, 8000))
  thinkingEscalateTimers.push(setTimeout(() => {
    if (isThinking.value) setThinkingPhase('仍在生成中，你可以随时点停止', { resetEscalate: false })
  }, 20000))
}

// Profile
const profileVisible = ref(false)
const profileLoading = ref(false)
const profileError = ref('')
const profile = ref(null)
const clearing = ref(false)

// Trace (right panel only — no duplicate strip above the input)
const traceMap = shallowReactive(new Map())
const traceSteps = computed(() =>
  Array.from(traceMap.values()).sort((a, b) => a.sequence - b.sequence)
)
const traceStatus = ref('')

// Quality
const qualityReview = ref(null)
const qualityBlocked = ref(null)
const showQualityDetail = ref(false)

// Artifact + workbench
const latestArtifact = ref(null)
const sessionArtifacts = ref([])
const roundArtifactId = ref(null)
const rightPanelTab = ref('artifact')
const rightPanelForcedClosed = ref(false)
const rightPanelOpen = computed(() => {
  if (rightPanelForcedClosed.value) return false
  return rightPanelTab.value === 'progress'
    || !!latestArtifact.value
    || sessionArtifacts.value.length > 0
    || agentProgressLog.value.length > 0
    || traceSteps.value.length > 0
})
const agentProgressLog = ref([])
const lastTraceId = ref(null)
const skillDraftBusy = ref(false)
const sandboxPolicyLabel = ref('')

// Expert packs + slash + tasks
const expertPacks = ref([])
const showPackPanel = ref(false)
const slashOpen = ref(false)
const slashFilter = ref('')
const skillCommands = ref([])
const myTasks = ref([])
const showTaskPanel = ref(false)

const BUILTIN_SLASH = [
  { id: 'interview', label: '/模拟面试', message: '帮我模拟一次面试，我在准备后端开发岗位' },
  { id: 'resume', label: '/改简历', message: '帮我优化简历，指出问题和改法' },
  { id: 'salary', label: '/谈涨薪', message: '我想跟公司谈涨薪，给我话术和策略' },
]

const filteredSlashCommands = computed(() => {
  const q = (slashFilter.value || '').replace(/^\//, '').toLowerCase()
  const fromSkills = (skillCommands.value || []).map(s => ({
    id: s.name,
    label: '/' + s.name,
    message: s.description || s.name,
    skill: true
  }))
  const all = [...BUILTIN_SLASH, ...fromSkills]
  if (!q) return all
  return all.filter(c => c.label.toLowerCase().includes(q) || (c.message || '').toLowerCase().includes(q))
})

// Suggested actions + feedback + companion + digital employees
const pendingSuggestedActions = ref([])
const coldStartChips = [
  { id: 'resume', label: '优化简历', message: '帮我看看简历有什么问题' },
  { id: 'salary', label: '谈涨薪', message: '我想跟公司谈涨薪，但不知道怎么开口' },
  { id: 'escape', label: '离职规划', message: '我在纠结要不要离职' },
  { id: 'interview', label: '面试准备', message: '帮我模拟一次面试，我在准备后端开发岗位' },
]
const showCompanionDrawer = ref(false)
const companionForm = ref({ displayName: '你的职场伙伴', tone: '简洁直接', focus: '', personaPrompt: '' })
const companionSaving = ref(false)
const companionMsg = ref('')
const showEmployeePanel = ref(false)
const employeeTemplates = ref([])
const myEmployees = ref([])
const employeeBusy = ref(false)
const employeeMsg = ref('')
const editingEmployee = ref(null)
const employeeEditForm = ref({ name: '', persona: '' })

const personaPresets = [
  {
    id: 'concise',
    label: '简洁直接',
    text: '回答先给结论，再列 3 条可执行步骤。语气简洁，少客套，不说废话。'
  },
  {
    id: 'coach',
    label: '教练式',
    text: '像职场教练：先确认目标，再给选项利弊，最后给推荐方案。鼓励但不空喊鸡汤。'
  },
  {
    id: 'strict',
    label: '严格挑刺',
    text: '以严格评审视角指出问题，优先找风险与漏洞，给出可落地的修改建议，避免虚假鼓励。'
  },
  {
    id: 'quant',
    label: '量化导向',
    text: '所有建议尽量能量化：用数字、对比、时间点表达。缺少数据时先问我补齐关键指标。'
  }
]

const activeEmployee = computed(() => myEmployees.value.find(e => e.active) || null)
const activeEmployeeLabel = computed(() =>
  activeEmployee.value ? `当前：${activeEmployee.value.name}` : (myEmployees.value.length ? `${myEmployees.value.length} 位待命` : '点此创建专精员工')
)
const activeEmployeeShort = computed(() =>
  activeEmployee.value ? activeEmployee.value.name : (myEmployees.value.length ? `${myEmployees.value.length} 位` : '去创建')
)

const openEmployeePanel = async () => {
  showEmployeePanel.value = true
  editingEmployee.value = null
  employeeMsg.value = ''
  await loadEmployees()
}

const closeEmployeePanel = () => {
  showEmployeePanel.value = false
  editingEmployee.value = null
  employeeMsg.value = ''
}

const enabledPackLabel = computed(() => {
  const n = expertPacks.value.filter(p => p.enabled).length
  return n ? `已启用 ${n} 个` : '点击配置'
})

const openPackPanel = async () => {
  showPackPanel.value = true
  try {
    const res = await listExpertPacks()
    expertPacks.value = res.data?.data || []
  } catch (e) {
    expertPacks.value = []
  }
}

const toggleExpertPack = async (pack) => {
  try {
    await setExpertPackEnabled(pack.packId, !pack.enabled)
    pack.enabled = !pack.enabled
  } catch (e) {
    console.error('切换专家包失败', e)
  }
}

const openTaskPanel = async () => {
  showTaskPanel.value = true
  try {
    const res = await listMyTasks()
    myTasks.value = res.data?.data || []
  } catch (e) {
    myTasks.value = []
  }
}

const onInputChange = () => {
  const v = inputMessage.value || ''
  if (v.startsWith('/')) {
    slashOpen.value = true
    slashFilter.value = v
  } else {
    slashOpen.value = false
    slashFilter.value = ''
  }
}

const applySlashCommand = (cmd) => {
  inputMessage.value = cmd.message
  slashOpen.value = false
  slashFilter.value = ''
}

const saveSkillFromLastTrace = async () => {
  skillDraftBusy.value = true
  try {
    let traceId = lastTraceId.value
    if (!traceId) {
      const { getTracesByChat } = await import('../api')
      const res = await getTracesByChat(currentChatId.value, 1, 1)
      const list = res.data?.data?.list || res.data?.data || []
      traceId = Array.isArray(list) ? list[0]?.traceId : list?.[0]?.traceId
    }
    if (!traceId) {
      alert('暂无可用 Trace，请先完成一轮对话')
      return
    }
    const draftRes = await draftSkillFromTrace(traceId)
    const draft = draftRes.data?.data
    if (!draft) {
      alert('未能从 Trace 生成技能草稿')
      return
    }
    const ok = window.confirm(`保存技能「${draft.name}」？\n${draft.description || ''}`)
    if (!ok) return
    await saveDraftSkill(draft)
    alert('技能已保存，可在 / 命令中使用')
    const skillsRes = await listSkills()
    skillCommands.value = skillsRes.data?.data || []
  } catch (e) {
    alert(e.message || '保存技能失败')
  } finally {
    skillDraftBusy.value = false
  }
}

const loadWorkbenchMeta = async () => {
  try {
    const [packsRes, skillsRes, policyRes, tasksRes] = await Promise.allSettled([
      listExpertPacks(),
      listSkills(),
      getSandboxPolicy(),
      listMyTasks()
    ])
    if (packsRes.status === 'fulfilled') expertPacks.value = packsRes.value.data?.data || []
    if (skillsRes.status === 'fulfilled') skillCommands.value = skillsRes.value.data?.data || []
    if (policyRes.status === 'fulfilled') {
      const p = policyRes.value.data?.data
      sandboxPolicyLabel.value = p?.label || p?.policy || ''
    }
    if (tasksRes.status === 'fulfilled') myTasks.value = tasksRes.value.data?.data || []
  } catch (e) { /* non-critical */ }
}

const startEmployeeEdit = (emp) => {
  editingEmployee.value = emp
  employeeEditForm.value = {
    name: emp.name || '',
    persona: emp.persona || ''
  }
  employeeMsg.value = ''
}

const cancelEmployeeEdit = () => {
  editingEmployee.value = null
  employeeEditForm.value = { name: '', persona: '' }
}

const applyPersonaPreset = (preset) => {
  const base = employeeEditForm.value.persona?.trim()
  const nameLine = employeeEditForm.value.name
    ? `你是「${employeeEditForm.value.name}」。`
    : ''
  employeeEditForm.value.persona = [nameLine, preset.text, base && !base.includes(preset.text) ? `\n补充：${base}` : '']
    .filter(Boolean)
    .join('\n')
}

const saveEmployeeEdit = async () => {
  if (!editingEmployee.value) return
  const name = employeeEditForm.value.name.trim()
  const persona = employeeEditForm.value.persona.trim()
  if (!name) {
    employeeMsg.value = '请填写员工名称'
    return
  }
  if (!persona) {
    employeeMsg.value = '请填写人设与规则'
    return
  }
  employeeBusy.value = true
  employeeMsg.value = ''
  try {
    await updateDigitalEmployee(editingEmployee.value.id, { name, persona })
    employeeMsg.value = '人设已保存（新版本生效）'
    editingEmployee.value = null
    await loadEmployees()
  } catch (e) {
    employeeMsg.value = e.message || '保存失败'
  } finally {
    employeeBusy.value = false
  }
}

// File upload + perception
const attachedFile = ref(null)
const fileInput = ref(null)
const attachHint = ref('resume')
const perceptionBusy = ref(false)
const lastPerceptionMeta = ref(null)

// Search
const searchKeyword = ref('')
const searchResults = ref([])
const isSearching = ref(false)
const searchMode = ref(false)

// Archived
const archivedSessions = ref([])
const showArchived = ref(false)

// Voice
const speechSupported = ref('webkitSpeechRecognition' in window || 'SpeechRecognition' in window)
const isListening = ref(false)
let recognition = null

let eventSource = null

const currentSessionTitle = computed(() => {
  const s = sessions.value.find(s => s.chatId === currentChatId.value)
  return s?.title || '新对话'
})

const communicationPreferenceText = computed(() => {
  const pref = profile.value?.communicationPreference
  if (pref === 'CONCISE') return '简洁'
  if (pref === 'DETAILED') return '详细'
  return '暂无'
})

// Quality review user-friendly text
const qualityIcon = computed(() => {
  const level = qualityReview.value?.riskLevel
  if (level === 'LOW') return '✅'
  if (level === 'MEDIUM') return '⚠️'
  return '🚫'
})
const qualityText = computed(() => {
  const level = qualityReview.value?.riskLevel
  if (level === 'LOW') return '回答可靠'
  if (level === 'MEDIUM') return '仅供参考，建议交叉验证'
  if (level === 'HIGH') return '该建议风险较高，请谨慎采纳'
  if (level === 'CRITICAL') return '回答存在严重风险，不建议直接采纳'
  return ''
})

// Session time grouping
const sessionGroups = computed(() => {
  const now = Date.now()
  const day = 86400000
  const groups = [
    { label: '今天', sessions: [] },
    { label: '最近 7 天', sessions: [] },
    { label: '更早', sessions: [] },
  ]
  for (const s of sessions.value) {
    const t = new Date(s.createTime || s.lastActiveAt).getTime()
    const diff = now - t
    if (diff < day) groups[0].sessions.push(s)
    else if (diff < 7 * day) groups[1].sessions.push(s)
    else groups[2].sessions.push(s)
  }
  return groups.filter(g => g.sessions.length > 0)
})

// Init
onMounted(async () => {
  try {
    await ensureLogin()
    await loadSessions()
    await loadWorkbenchMeta()

    // Check if resuming a session via query param
    const route = router.currentRoute.value
    const resumeId = route.query.chatId
    if (resumeId) {
      await switchSession(resumeId)
    } else if (sessions.value.length > 0) {
      await switchSession(sessions.value[0].chatId)
    } else {
      await createNewSession()
    }

    // Check for pending message from Home (via query param)
    const pendingMsg = route.query.msg
    if (pendingMsg) {
      inputMessage.value = pendingMsg
      // Clean up URL without re-triggering navigation
      router.replace({ path: '/chat/career' })
    }

    await Promise.all([loadCompanion(), loadEmployees()])
  } catch (e) {
    console.error('初始化失败', e)
  }
})

onBeforeUnmount(() => {
  if (eventSource) eventSource.close()
  clearThinkingEscalate()
})

const ensureLogin = async () => {
  if (localStorage.getItem('token')) {
    try {
      await getMe()
      return
    } catch (_) {
      /* token invalid */
    }
  }
  // Guest login disabled by default — force register/login
  localStorage.removeItem('token')
  router.replace({ path: '/login', query: { redirect: '/chat/career' } })
  throw new Error('NOT_LOGGED_IN')
}

const loadSessions = async () => {
  try {
    const res = await listSessions()
    sessions.value = res.data.data || []
  } catch (e) { sessions.value = [] }
}

const createNewSession = async () => {
  try {
    const res = await createSession('新对话')
    const session = res.data.data
    sessions.value.unshift(session)
    currentChatId.value = session.chatId
    messages.value = []
  } catch (e) {
    currentChatId.value = 'local_' + Math.random().toString(36).slice(2, 10)
    messages.value = []
  }
}

const switchSession = async (chatId) => {
  if (eventSource) { eventSource.close(); isStreaming.value = false }
  currentChatId.value = chatId
  messages.value = []
  latestArtifact.value = null
  sessionArtifacts.value = []
  agentProgressLog.value = []
  roundArtifactId.value = null
  isLoadingHistory.value = true
  try {
    const res = await getChatMessages(chatId)
    const history = res.data?.data || []
    if (history.length > 0) {
      messages.value = history.map(m => ({
        content: m.content || m.partialContent || '',
        isUser: m.role === 'user',
        type: m.status === 'PARTIAL' ? 'partial' : '',
        time: m.timestamp || Date.now(),
        messageId: m.messageId,
        status: m.status || 'COMPLETE',
        agentType: m.sourceType || m.sourceId || (m.role === 'user' ? null : 'GENERAL'),
        agentName: m.sourceName || null
      }))
    }
    await loadSessionArtifacts(chatId)
  } catch (e) {
    console.error('加载历史消息失败', e)
  } finally {
    isLoadingHistory.value = false
  }
}

const loadSessionArtifacts = async (chatId) => {
  try {
    const res = await listMyArtifacts(chatId)
    sessionArtifacts.value = res.data?.data || []
    if (sessionArtifacts.value.length && !latestArtifact.value) {
      await openArtifact(sessionArtifacts.value[0].artifactId)
      openRightPanel('artifact')
    }
  } catch (e) {
    sessionArtifacts.value = []
  }
}

const openArtifact = async (artifactId) => {
  if (!artifactId) return
  try {
    const res = await getMyArtifactDetail(artifactId)
    latestArtifact.value = res.data?.data || null
    openRightPanel('artifact')
  } catch (e) {
    console.error('加载交付物失败', e)
  }
}

const closeRightPanel = () => {
  latestArtifact.value = null
  rightPanelForcedClosed.value = true
  rightPanelTab.value = 'artifact'
}

const openRightPanel = (tab = 'artifact') => {
  rightPanelForcedClosed.value = false
  rightPanelTab.value = tab
}

const removeSession = async (chatId) => {
  try {
    await deleteSession(chatId)
    const removedSession = sessions.value.find(s => s.chatId === chatId)
    sessions.value = sessions.value.filter(s => s.chatId !== chatId)
    if (currentChatId.value === chatId) {
      if (sessions.value.length > 0) switchSession(sessions.value[0].chatId)
      else await createNewSession()
    }
    // Show undo toast
    undoToast.value = { chatId, title: removedSession?.title }
    clearTimeout(undoTimer)
    undoTimer = setTimeout(() => { undoToast.value = null }, 5000)
  } catch (e) { console.error('删除失败', e) }
}

const handleUndoDelete = async () => {
  if (!undoToast.value) return
  try {
    const { restoreSession } = await import('../api')
    await restoreSession(undoToast.value.chatId)
    await loadSessions()
    undoToast.value = null
  } catch (e) { console.error('撤销失败', e) }
}

// Rename
const startRename = (session) => {
  session.editing = true
  session.newTitle = session.title
}
const saveRename = async (session) => {
  if (!session.newTitle || session.newTitle.trim() === session.title) {
    session.editing = false; return
  }
  try {
    await renameSession(session.chatId, session.newTitle.trim())
    session.title = session.newTitle.trim()
  } catch (e) { console.error('重命名失败', e) }
  session.editing = false
}

// Archive
const handleArchive = async (chatId) => {
  try {
    await archiveSession(chatId)
    sessions.value = sessions.value.filter(s => s.chatId !== chatId)
    if (currentChatId.value === chatId) {
      if (sessions.value.length > 0) switchSession(sessions.value[0].chatId)
      else await createNewSession()
    }
  } catch (e) { console.error('归档失败', e) }
}

const toggleArchived = async () => {
  if (!showArchived.value) {
    try {
      const res = await listArchivedSessions()
      archivedSessions.value = res.data?.data || []
    } catch (e) { archivedSessions.value = [] }
  }
  showArchived.value = !showArchived.value
}

const handleUnarchive = async (chatId) => {
  try {
    await unarchiveSession(chatId)
    archivedSessions.value = archivedSessions.value.filter(s => s.chatId !== chatId)
    const res = await listSessions()
    sessions.value = res.data?.data || []
  } catch (e) { console.error('取消归档失败', e) }
}

// Search
let searchTimer = null
const onSearchInput = () => {
  clearTimeout(searchTimer)
  if (!searchKeyword.value.trim()) { searchMode.value = false; searchResults.value = []; return }
  searchTimer = setTimeout(doSearch, 300)
}
const doSearch = async () => {
  const kw = searchKeyword.value.trim()
  if (!kw) return
  isSearching.value = true; searchMode.value = true
  try {
    const res = await searchSessions(kw)
    searchResults.value = res.data?.data || []
  } catch (e) { searchResults.value = [] }
  finally { isSearching.value = false }
}
const clearSearch = () => {
  searchKeyword.value = ''; searchResults.value = []; searchMode.value = false
}

// File upload + perception preprocess
const onFileSelected = (e) => {
  const file = e.target.files[0]
  if (!file) return
  if (file.size > 10 * 1024 * 1024) { alert('文件不能超过 10MB'); return }
  const name = (file.name || '').toLowerCase()
  const ok = /\.(pdf|docx|txt|md|csv|png|jpe?g|webp|gif)$/.test(name)
  if (!ok) {
    alert('暂支持 PDF / Word(.docx) / 文本 / 图片。扫描件 PDF 需文字层；纯图暂无 OCR')
    e.target.value = ''
    return
  }
  attachedFile.value = file
  lastPerceptionMeta.value = null
  // Heuristic hint from filename
  if (/offer|薪|salary|薪资/.test(name)) attachHint.value = 'offer'
  else if (/resume|简历|cv/.test(name)) attachHint.value = 'resume'
  e.target.value = ''
}

const clearAttachedFile = () => {
  attachedFile.value = null
  lastPerceptionMeta.value = null
}

// Favorite
const toggleFavorite = async (msg, index) => {
  if (msg.favorited && msg.favoriteId) {
    try { await removeFavorite(msg.favoriteId); msg.favorited = false; msg.favoriteId = null }
    catch (e) { console.error('取消收藏失败', e) }
  } else {
    try {
      const res = await addFavorite(currentChatId.value, msg.messageId || '', msg.content, msg.isUser ? 'user' : 'assistant')
      msg.favorited = true; msg.favoriteId = res.data?.data?.favoriteId
    } catch (e) { console.error('收藏失败', e) }
  }
}

const sendSuggested = (message) => {
  if (!message || isStreaming.value) return
  inputMessage.value = message
  sendMessage()
}

const rateMessage = async (msg, rating) => {
  if (!msg || msg.feedbackRating || msg.isUser) return
  if (!msg.messageId) {
    console.warn('missing messageId for feedback')
    return
  }
  try {
    await submitFeedback(currentChatId.value, msg.messageId, rating, {
      agentType: msg.agentType,
      intent: msg.agentType
    })
    msg.feedbackRating = rating
  } catch (e) {
    console.error('提交反馈失败', e)
  }
}

const loadCompanion = async () => {
  try {
    const res = await getMyCompanion()
    const data = res.data?.data
    if (data) {
      companionForm.value = {
        displayName: data.displayName || '你的职场伙伴',
        tone: data.stylePrefs?.tone || '简洁直接',
        focus: data.stylePrefs?.focus || '',
        personaPrompt: data.personaPrompt || ''
      }
    }
  } catch (e) {
    console.warn('load companion failed', e)
  }
}

const saveCompanion = async () => {
  companionSaving.value = true
  companionMsg.value = ''
  try {
    await updateMyCompanion({
      displayName: companionForm.value.displayName,
      personaPrompt: companionForm.value.personaPrompt,
      stylePrefs: {
        tone: companionForm.value.tone,
        focus: companionForm.value.focus
      }
    })
    companionMsg.value = '已保存，下一轮对话生效'
  } catch (e) {
    companionMsg.value = e.message || '保存失败'
  } finally {
    companionSaving.value = false
  }
}

const loadEmployees = async () => {
  try {
    const [tplRes, listRes] = await Promise.all([
      listDigitalEmployeeTemplates(),
      listMyDigitalEmployees()
    ])
    employeeTemplates.value = tplRes.data?.data || []
    myEmployees.value = listRes.data?.data || []
  } catch (e) {
    console.warn('load employees failed', e)
  }
}

const createFromTemplate = async (tpl) => {
  employeeBusy.value = true
  employeeMsg.value = ''
  try {
    const res = await createDigitalEmployee({
      templateCode: tpl.agentCode,
      name: tpl.displayName,
      persona: tpl.description || `你是「${tpl.displayName}」，专注帮助用户解决相关职场问题。`
    })
    const created = res.data?.data
    employeeMsg.value = `已创建「${tpl.displayName}」，可以继续自定义人设`
    await loadEmployees()
    if (created) {
      startEmployeeEdit(created)
    }
  } catch (e) {
    employeeMsg.value = e.message || '创建失败'
  } finally {
    employeeBusy.value = false
  }
}

const activateEmployee = async (emp) => {
  try {
    await setActiveDigitalEmployee(emp.id)
    employeeMsg.value = `已切换到「${emp.name}」`
    await loadEmployees()
  } catch (e) {
    employeeMsg.value = e.message || '切换失败'
  }
}

const rollbackEmployee = async (emp) => {
  try {
    await rollbackDigitalEmployee(emp.id, emp.configVersion - 1)
    employeeMsg.value = '已回滚到上一版'
    await loadEmployees()
  } catch (e) {
    employeeMsg.value = e.message || '回滚失败'
  }
}

// Voice
const toggleVoice = () => { isListening.value ? stopVoice() : startVoice() }
const startVoice = () => {
  if (!speechSupported.value) return
  const SR = window.SpeechRecognition || window.webkitSpeechRecognition
  recognition = new SR()
  recognition.lang = 'zh-CN'; recognition.continuous = false; recognition.interimResults = true
  recognition.onresult = (e) => {
    inputMessage.value = Array.from(e.results).map(r => r[0].transcript).join('')
  }
  recognition.onerror = () => { isListening.value = false }
  recognition.onend = () => { isListening.value = false }
  recognition.start(); isListening.value = true
}
const stopVoice = () => { if (recognition) { recognition.stop(); recognition = null } isListening.value = false }

// Helpers
const addMessage = (content, isUser, type = '') => {
  messages.value.push({ content, isUser, type, time: Date.now() })
  scrollToBottom()
}
const getAgentAvatar = (agentType) => {
  const m = { RESUME: '📄', NEGOTIATION: '💰', ESCAPE: '🚪', CONSULTATION: '📅', DATA_QUERY: '📊', GENERAL: '🟡' }
  return m[agentType] || '🟡'
}
const scrollToBottom = async () => {
  await nextTick()
  if (messagesContainer.value) messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
}
const renderMarkdown = (text) => {
  if (!text) return ''
  // ATX 标题需「# 后空格」；模型常写成 ###🔍，否则会原样露出 ###
  const cleaned = text
    .replace(/<!--\s*hitl:[^>]+-->/gi, '')
    .replace(/^(#{1,6})([^\s#])/gm, '$1 $2')
  return DOMPurify.sanitize(marked.parse(cleaned))
}

const extractHitlId = (text) => {
  if (!text) return null
  const m = text.match(/<!--\s*hitl:([a-f0-9-]+)\s*-->/i)
  return m ? m[1] : null
}

const handleHitlApprove = async (msg) => {
  if (!msg?.hitlApprovalId || isStreaming.value) return
  msg.hitlBusy = true
  try {
    // 只发聊天「确认创建」，由后端统一 approve + 创建，避免按钮先 approve 再重复报错
    msg.hitlResolved = true
    inputMessage.value = '确认创建'
    await nextTick()
    await sendMessage()
  } catch (e) {
    msg.hitlResolved = false
    addMessage(`审批失败：${e.response?.data?.message || e.message || '请重试'}`, false, 'error')
  } finally {
    msg.hitlBusy = false
  }
}

const handleHitlReject = async (msg) => {
  if (!msg?.hitlApprovalId || isStreaming.value) return
  msg.hitlBusy = true
  try {
    msg.hitlResolved = true
    inputMessage.value = '取消'
    await nextTick()
    await sendMessage()
  } catch (e) {
    msg.hitlResolved = false
    addMessage(`取消失败：${e.response?.data?.message || e.message || '请重试'}`, false, 'error')
  } finally {
    msg.hitlBusy = false
  }
}
const formatTimeAgo = (val) => {
  if (!val) return ''
  const d = new Date(val); if (isNaN(d.getTime())) return ''
  const diff = Date.now() - d
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前'
  if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前'
  return d.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })
}

// Retry last message on error
const retrySendMessage = () => {
  if (!lastUserMessage.value || isStreaming.value) return
  // Remove the error message
  const lastMsg = messages.value[messages.value.length - 1]
  if (lastMsg && lastMsg.type === 'error') {
    messages.value.pop()
  }
  // Re-set input and send
  inputMessage.value = lastUserMessage.value
  nextTick(() => sendMessage())
}

// Stop generation
const stopGeneration = () => {
  if (eventSource) { eventSource.close(); eventSource = null }
  isStreaming.value = false
  isThinking.value = false
  clearThinkingEscalate()
  // Append interruption marker to last AI message
  const lastMsg = messages.value[messages.value.length - 1]
  if (lastMsg && !lastMsg.isUser && lastMsg.content) {
    lastMsg.content += '\n\n…（已停止生成）'
  }
}

// Copy message content
const copyMessage = async (content) => {
  try {
    // Strip markdown for plain text copy
    const plain = content.replace(/[#*`_~\[\]()>]/g, '').trim()
    await navigator.clipboard.writeText(plain)
  } catch (e) {
    // Fallback for older browsers
    const ta = document.createElement('textarea')
    ta.value = content; document.body.appendChild(ta); ta.select()
    document.execCommand('copy'); document.body.removeChild(ta)
  }
}

// Send message (SSE streaming)
const sendMessage = async () => {
  if (isStreaming.value || perceptionBusy.value) return
  const msg = inputMessage.value.trim()
  const file = attachedFile.value
  if (!msg && !file) return

  inputMessage.value = ''
  attachedFile.value = null

  lastUserMessage.value = msg
  let finalMsg = msg
  const displayMsg = file
    ? (msg ? `📎 ${file.name}\n${msg}` : `📎 ${file.name}\n请根据材料帮我分析并给出可执行建议`)
    : msg

  if (file) {
    perceptionBusy.value = true
    lastPerceptionMeta.value = null
    try {
      if (!currentChatId.value) {
        throw new Error('请先创建或选择会话')
      }
      const res = await preprocessPerceptionAndBind(file, currentChatId.value, attachHint.value)
      const data = res.data?.data
      if (!data) {
        throw new Error(res.data?.message || '感知结果为空')
      }
      // Shared State 已绑定材料；SSE 只发短句，避免 URL 过长（须含「简历/Offer」关键词以便路由）
      if (msg) {
        finalMsg = msg
      } else if (attachHint.value === 'offer') {
        finalMsg = '请帮我分析这份 Offer/薪资材料，给出谈判建议。'
      } else {
        finalMsg = '请帮我优化这份简历，指出问题和改法。'
      }
      const fieldCount = data.structuredFields
        ? Object.keys(data.structuredFields).length
        : 0
      lastPerceptionMeta.value = {
        confidence: Number(data.confidence) || 0,
        injectionRisk: !!data.injectionRisk,
        fieldCount,
        notes: data.notes || ''
      }
      if (data.notes) {
        console.info('[perception]', data.notes)
      }
    } catch (e) {
      perceptionBusy.value = false
      const errText = e.response?.data?.message || e.message || '请重试'
      addMessage(`⚠️ 感知预处理失败: ${errText}`, false)
      if (!msg) return
      finalMsg = msg
    } finally {
      perceptionBusy.value = false
    }
  }

  addMessage(displayMsg, true)
  isThinking.value = true
  isStreaming.value = true
  setThinkingPhase(file ? '感知完成，正在路由专家…' : '正在分析…', { resetEscalate: false })
  startThinkingEscalate()
  currentAgent.value = { name: '分析中...', type: 'general' }

  if (eventSource) eventSource.close()
  eventSource = chatWithOrchestrator(finalMsg || msg, currentChatId.value)

  traceMap.clear()
  rightPanelForcedClosed.value = false
  rightPanelTab.value = 'progress'
  qualityReview.value = null
  qualityBlocked.value = null
  showQualityDetail.value = false
  pendingSuggestedActions.value = []
  roundArtifactId.value = null
  agentProgressLog.value = []

  let aiMsgIndex = -1
  let currentAgentInfo = { agentType: 'GENERAL', agentName: '职场通用顾问' }
  let currentAssistantMessageId = null
  let resumeAttempts = 0

  const bindStreamHandlers = (es, { isResume = false } = {}) => {
    es.addEventListener('routing', (e) => {
      // Keep thinking bubble until first token; soft status only (tech detail → right panel)
      const text = (e.data || '').replace(/^\[|\]$/g, '')
      if (text.includes('路由到')) {
        setThinkingPhase('专家已就位，正在准备回答…')
        addMessage(e.data, false, 'routing')
        const routeMatch = text.match(/路由到(.+?)$/)
        if (routeMatch) currentAgent.value = { name: routeMatch[1], type: 'general' }
      } else if (text.includes('正在分析') || text.includes('正在理解')) {
        setThinkingPhase('正在理解你的问题…')
      } else if (text) {
        setThinkingPhase(text.endsWith('…') || text.endsWith('...') ? text : `${text}…`)
      }
    })

    es.addEventListener('agent-turn', (e) => {
      try {
        const data = JSON.parse(e.data)
        currentAgentInfo = { agentType: data.agentType, agentName: data.agentName }
        currentAgent.value = { name: data.agentName, type: data.agentType }
        aiMsgIndex = -1
      } catch (err) {}
    })

    es.addEventListener('message-start', (e) => {
      try {
        const data = JSON.parse(e.data)
        currentAssistantMessageId = data.assistantMessageId
        setThinkingPhase('正在写下回答…')
        if (aiMsgIndex === -1 || isResume) {
          if (aiMsgIndex === -1) {
            messages.value.push({
              content: '', isUser: false, type: '', time: Date.now(),
              messageId: currentAssistantMessageId,
              status: 'STREAMING',
              agentType: currentAgentInfo.agentType, agentName: currentAgentInfo.agentName
            })
            aiMsgIndex = messages.value.length - 1
          } else if (messages.value[aiMsgIndex]) {
            messages.value[aiMsgIndex].messageId = currentAssistantMessageId
            messages.value[aiMsgIndex].status = 'STREAMING'
            if (isResume) messages.value[aiMsgIndex].content = ''
          }
        } else if (messages.value[aiMsgIndex]) {
          messages.value[aiMsgIndex].messageId = currentAssistantMessageId
          messages.value[aiMsgIndex].status = 'STREAMING'
        }
      } catch (err) {}
    })

    es.addEventListener('trace', (e) => {
      try {
        const data = JSON.parse(e.data)
        switch (data.type) {
          case 'TRACE_STARTED':
            traceStatus.value = 'RUNNING'
            if (data.traceId) lastTraceId.value = data.traceId
            break
          case 'SPAN_STARTED': {
            const span = {
              sequence: data.sequence, stepType: data.stepType,
              stepTypeDisplayName: data.stepTypeDisplayName,
              label: data.label, status: 'RUNNING', errorMessage: null,
              startTime: new Date().toISOString(),
              metadata: data.metadata || null
            }
            traceMap.set(data.sequence, span)
            const friendly = friendlyLabelFromSpan(span)
            if (friendly) setThinkingPhase(friendly)
            break
          }
          case 'SPAN_ENDED': {
            const span = traceMap.get(data.sequence)
            if (span) { span.status = data.status; span.errorMessage = data.errorMessage; span.endTime = new Date().toISOString() }
            break }
          case 'TRACE_COMPLETED': traceStatus.value = 'SUCCESS'; break
          case 'TRACE_FAILED': traceStatus.value = 'FAILED'; break
        }
      } catch (err) {}
    })

    es.addEventListener('agent-progress', (e) => {
      try {
        const data = JSON.parse(e.data)
        agentProgressLog.value.push(data)
        if (data.status === 'started') {
          setThinkingPhase(`${data.agent || '专家'}正在认真作答…`)
        }
        // Technical progress stays in the right panel only
        if (rightPanelTab.value !== 'progress') openRightPanel('progress')
      } catch (err) {}
    })

    es.addEventListener('artifact-ready', async (e) => {
      try {
        const data = JSON.parse(e.data)
        roundArtifactId.value = data.artifactId
        await openArtifact(data.artifactId)
        await loadSessionArtifacts(currentChatId.value)
      } catch (err) {
        console.error('artifact-ready handle failed', err)
      }
    })

    es.addEventListener('artifact-adopted', async () => {
      await loadSessionArtifacts(currentChatId.value)
    })

    es.addEventListener('quality-review', (e) => {
      try { qualityReview.value = JSON.parse(e.data) } catch (err) {}
    })
    es.addEventListener('quality-blocked', (e) => { qualityBlocked.value = e.data })
    es.addEventListener('clarification', (e) => {
      isThinking.value = false
      clearThinkingEscalate()
      addMessage(e.data, false, 'clarification')
      isStreaming.value = false; es.close()
    })
    es.addEventListener('status', (e) => {
      try {
        const data = JSON.parse(e.data)
        if (data.resume === 'partial' && aiMsgIndex >= 0) {
          messages.value[aiMsgIndex].status = 'PARTIAL'
          messages.value[aiMsgIndex].type = 'partial'
        }
      } catch (err) {}
    })
    es.addEventListener('suggested-actions', (e) => {
      try {
        pendingSuggestedActions.value = JSON.parse(e.data) || []
      } catch (err) {
        pendingSuggestedActions.value = []
      }
    })

    es.onmessage = (e) => {
      isThinking.value = false
      clearThinkingEscalate()
      if (e.data === '[DONE]') {
        isStreaming.value = false
        es.close()
        if (aiMsgIndex >= 0 && messages.value[aiMsgIndex]?.status === 'STREAMING') {
          messages.value[aiMsgIndex].status = 'COMPLETE'
        }
        if (aiMsgIndex >= 0 && messages.value[aiMsgIndex]) {
          const hitlId = extractHitlId(messages.value[aiMsgIndex].content)
          if (hitlId) {
            messages.value[aiMsgIndex].hitlApprovalId = hitlId
            messages.value[aiMsgIndex].hitlResolved = false
          }
          if (pendingSuggestedActions.value.length) {
            messages.value[aiMsgIndex].suggestedActions = pendingSuggestedActions.value
            pendingSuggestedActions.value = []
          }
          if (roundArtifactId.value) {
            messages.value[aiMsgIndex].artifactId = roundArtifactId.value
          }
        }
        if (!isResume) autoNameSession(msg)
        return
      }
      if (aiMsgIndex === -1) {
        messages.value.push({
          content: '', isUser: false, type: '', time: Date.now(),
          messageId: currentAssistantMessageId,
          status: 'STREAMING',
          agentType: currentAgentInfo.agentType, agentName: currentAgentInfo.agentName
        })
        aiMsgIndex = messages.value.length - 1
      }
      messages.value[aiMsgIndex].content += e.data
      scrollToBottom()
    }

    es.onerror = () => {
      es.close()
      const hasContent = aiMsgIndex >= 0 && messages.value[aiMsgIndex]?.content
      if (currentAssistantMessageId && resumeAttempts < 2) {
        resumeAttempts += 1
        setThinkingPhase('连接中断，正在续传…')
        isThinking.value = true
        startThinkingEscalate()
        setTimeout(() => {
          const resumeEs = resumeOrchestratorChat(currentChatId.value, currentAssistantMessageId)
          eventSource = resumeEs
          bindStreamHandlers(resumeEs, { isResume: true })
        }, 600 * resumeAttempts)
        return
      }
      isThinking.value = false
      clearThinkingEscalate()
      isStreaming.value = false
      if (aiMsgIndex >= 0) {
        messages.value[aiMsgIndex].status = 'PARTIAL'
        messages.value[aiMsgIndex].type = 'partial'
      }
      if (!hasContent) {
        addMessage('连接出现问题，请重试。', false, 'error')
      } else {
        addMessage('连接中断，已保留部分回答（可刷新会话查看）。', false, 'error')
      }
    }
  }

  bindStreamHandlers(eventSource)
}

// Auto-name session after first AI reply (if still "新对话")
const autoNameSession = async (userMsg) => {
  const session = sessions.value.find(s => s.chatId === currentChatId.value)
  if (!session || session.title !== '新对话') return
  // Use first 15 chars of user message as title
  const title = userMsg.length > 15 ? userMsg.slice(0, 15) + '…' : userMsg
  try {
    await renameSession(session.chatId, title)
    session.title = title
  } catch (e) { /* non-critical, ignore */ }
}

// Profile
const openProfile = async () => {
  profileVisible.value = true; profileError.value = ''; profileLoading.value = true
  try {
    await ensureLogin()
    const res = await getMyProfile()
    profile.value = res.data?.data || null
  } catch (e) {
    profileError.value = '加载画像失败，请稍后重试。'; profile.value = null
  } finally { profileLoading.value = false }
}
const closeProfile = () => { profileVisible.value = false }
const handleClearProfile = async () => {
  if (clearing.value) return
  if (!window.confirm('确定要清空你的画像吗？此操作不可恢复。')) return
  clearing.value = true
  try { await clearMyProfile(); profile.value = null }
  catch (e) { console.error('清空画像失败', e) }
  finally { clearing.value = false }
}
</script>

<style scoped>
.chat-layout {
  display: flex;
  height: 100%;
  overflow: hidden;
}

/* ===== Sidebar ===== */
.sidebar {
  width: 272px;
  flex-shrink: 0;
  background: var(--deep);
  border-right: 1px solid var(--glass-border);
  display: flex;
  flex-direction: column;
  transition: width 0.3s var(--ease);
}
.sidebar.collapsed { width: 48px; }

.side-head {
  padding: 18px 18px 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.side-head h2 {
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 1px;
  color: var(--t4);
  text-transform: uppercase;
  flex: 1;
}
.collapse-toggle {
  width: 24px; height: 24px; border-radius: 4px;
  display: flex; align-items: center; justify-content: center;
  color: var(--t4); font-size: 14px; cursor: pointer;
  background: transparent; border: none;
  transition: background 0.2s;
}
.collapse-toggle:hover { background: var(--glass-hover); }

.side-search {
  margin: 0 14px 12px;
  position: relative;
}
.side-search input {
  width: 100%;
  padding: 9px 12px 9px 34px;
  background: var(--layer1);
  border: 1px solid var(--glass-border);
  border-radius: var(--r-sm);
  color: var(--t1);
  font-size: 12px;
  outline: none;
  transition: border-color 0.2s;
}
.side-search input:focus { border-color: var(--gold-border); }
.side-search input::placeholder { color: var(--t4); }
.side-search .s-icon { position: absolute; left: 11px; top: 50%; transform: translateY(-50%); color: var(--t4); }
.search-clear {
  position: absolute; right: 8px; top: 50%; transform: translateY(-50%);
  background: none; border: none; color: var(--t4); cursor: pointer; font-size: 14px;
}
.search-status { padding: 12px 18px; font-size: 12px; color: var(--t4); }

.conv-list { flex: 1; overflow-y: auto; padding: 0 10px 10px; }
.conv {
  padding: 11px 13px;
  border-radius: var(--r-sm);
  cursor: pointer;
  transition: all 0.2s var(--ease);
  margin-bottom: 2px;
}
.conv:hover { background: var(--glass); }
.conv.on { background: var(--glass); border: 1px solid var(--glass-border); }
.c-title { font-size: 13px; font-weight: 500; color: var(--t1); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; margin-bottom: 3px; }
.c-meta { display: flex; align-items: center; justify-content: space-between; font-size: 11px; color: var(--t4); }
.c-actions { display: flex; gap: 2px; opacity: 0; transition: opacity 0.2s; }
.conv:hover .c-actions { opacity: 1; }
.c-act { background: none; border: none; color: var(--t4); cursor: pointer; font-size: 11px; padding: 2px 4px; border-radius: 4px; }
.c-act:hover { background: var(--glass-hover); color: var(--t2); }
.rename-input {
  width: 100%; padding: 4px 8px; background: var(--layer1);
  border: 1px solid var(--glass-border); border-radius: 4px;
  color: var(--t1); font-size: 12px; outline: none;
}
.empty-conv { padding: 20px; text-align: center; color: var(--t4); font-size: 12px; }

.archived-section { padding: 8px 14px; border-top: 1px solid var(--glass-border); }
.archived-toggle {
  background: none; border: none; color: var(--t4); font-size: 11px;
  cursor: pointer; padding: 4px 0;
}
.archived-toggle:hover { color: var(--t3); }
.conv.archived { opacity: 0.6; }
.archived-list { max-height: 200px; overflow-y: auto; }

/* ===== Chat Core ===== */
.chat-core {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.chat-top {
  padding: 14px 22px;
  border-bottom: 1px solid var(--glass-border);
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}
.back-mobile { display: none; width: 32px; height: 32px; border-radius: var(--r-sm); border: none; background: transparent; color: var(--t3); cursor: pointer; font-size: 16px; }
.chat-top-info { flex: 1; }
.chat-top-info h3 { font-size: 15px; font-weight: 600; color: var(--t1); }
.chat-top-info .agent-tag { font-size: 11px; color: var(--t4); margin-top: 1px; }
.chat-top-actions { display: flex; gap: 6px; align-items: center; }
.top-pill {
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  border: 1px solid var(--glass-border);
  background: var(--glass);
  color: var(--t1);
  border-radius: 999px;
  padding: 5px 10px;
  font-size: 12px;
  cursor: pointer;
}
.top-pill.companion { border-color: var(--gold-border); color: var(--gold-text); background: var(--gold-soft); }
.top-pill.employee { border-color: var(--gold-border); color: var(--gold-text); background: var(--gold-soft); }
.top-pill:hover { background: var(--glass-hover); }

.capability-cards {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin: 16px 0 8px;
}
.cap-card {
  text-align: left;
  border: 1px solid var(--glass-border);
  background: var(--glass);
  border-radius: 14px;
  padding: 14px;
  cursor: pointer;
}
.cap-card:hover { border-color: var(--gold); }
.cap-card-kicker {
  display: block;
  font-size: 11px;
  color: var(--t3);
  margin-bottom: 4px;
}
.cap-card strong {
  display: block;
  font-size: 14px;
  color: var(--t1);
  margin-bottom: 6px;
}
.cap-card p {
  margin: 0;
  font-size: 12px;
  color: var(--t2);
  line-height: 1.45;
}
.cap-card-cta {
  display: inline-block;
  margin-top: 10px;
  font-size: 12px;
  color: var(--gold-text);
  font-weight: 600;
}

.team-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}
.team-strip-btn {
  border: 1px solid var(--glass-border);
  background: var(--glass);
  color: var(--t1);
  border-radius: 999px;
  padding: 5px 11px;
  font-size: 12px;
  cursor: pointer;
}
.team-strip-btn.accent {
  border-color: var(--gold-border);
  color: var(--gold-text);
  background: var(--gold-soft);
}
.team-strip-btn.ghost { background: transparent; color: var(--t3); }
.team-strip-btn:hover { background: var(--glass-hover); }

.side-team {
  margin-top: auto;
  padding: 12px 10px 14px;
  border-top: 1px solid var(--glass-border);
}
.side-team-label {
  font-size: 11px;
  color: var(--t3);
  margin: 0 4px 8px;
  letter-spacing: 0.02em;
}
.side-team-item {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
  text-align: left;
  border: 1px solid transparent;
  background: transparent;
  border-radius: 10px;
  padding: 8px;
  cursor: pointer;
  margin-bottom: 4px;
}
.side-team-item:hover {
  background: var(--glass-hover);
  border-color: var(--glass-border);
}
.side-team-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}
.side-team-dot.companion { background: var(--gold); }
.side-team-dot.employee { background: var(--gold-text); }
.side-team-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.side-team-text strong {
  font-size: 13px;
  color: var(--t1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.side-team-text small {
  font-size: 11px;
  color: var(--t3);
}

@media (max-width: 720px) {
  .capability-cards { grid-template-columns: 1fr; }
  .top-pill { display: none; }
}

/* More menu */
.more-menu-wrap { position: relative; }
.more-menu {
  position: absolute; top: 38px; right: 0; z-index: 100;
  background: var(--layer1); border: 1px solid var(--glass-border);
  border-radius: var(--r-sm); padding: 6px 0; min-width: 160px;
  box-shadow: var(--card-hover-shadow);
}
.menu-item {
  display: flex; align-items: center; gap: 10px;
  padding: 10px 16px; font-size: 13px; color: var(--t2); cursor: pointer;
  transition: background 0.15s;
}
.menu-item:hover { background: var(--glass-hover); color: var(--t1); }

/* Messages */
.msgs {
  flex: 1;
  overflow-y: auto;
  padding: 24px 22px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

/* Presence (welcome) */
.presence { display: flex; align-items: flex-start; gap: 14px; margin-bottom: 26px; }
.ai-orb {
  flex-shrink: 0; width: 38px; height: 38px; border-radius: 50%;
  background: var(--logo-orb);
  box-shadow: 0 0 20px var(--gold-glow), inset 0 1px 0 rgba(255,255,255,0.25);
  animation: orb-pulse 6s ease-in-out infinite;
}
.presence-body {
  flex: 1; background: var(--layer1); border: 1px solid var(--glass-border);
  border-radius: var(--r-lg); border-top-left-radius: 4px; padding: 4px;
}
.presence-text {
  padding: 20px 24px; font-size: 14px; color: var(--t2); line-height: 1.8;
}
.presence-text b { color: var(--t1); font-weight: 600; }
.presence-text .hl { color: var(--gold-text); font-weight: 500; }
.presence-privacy {
  margin-top: 16px; font-size: 11px; color: var(--t4); opacity: 0.7;
  display: inline-flex; align-items: center; gap: 6px;
}

/* Message rows */
.msg-wrapper { margin-bottom: 6px; }
.msg-row { display: flex; gap: 14px; }
.msg-row.you { flex-direction: row-reverse; }
.msg-av { flex-shrink: 0; width: 30px; height: 30px; border-radius: 50%; background: var(--layer2); display: flex; align-items: center; justify-content: center; font-size: 13px; }
.msg-bub {
  max-width: 70%; padding: 16px 20px; border-radius: var(--r-lg);
  font-size: 14px; line-height: 1.75;
}
.msg-row.ai .msg-bub { background: var(--layer1); border: 1px solid var(--glass-border); border-top-left-radius: 4px; color: var(--t1); }
.msg-row.you .msg-bub { background: linear-gradient(135deg, var(--gold-soft), var(--gold-dim)); border: 1px solid var(--gold-border-soft); color: var(--t1); }
.agent-label { font-size: 11px; color: var(--gold-text); margin-bottom: 4px; font-weight: 500; }
.msg-actions { margin-top: 4px; }
.msg-act { background: none; border: none; cursor: pointer; font-size: 14px; padding: 2px 6px; border-radius: 4px; color: var(--t4); }
.msg-act:hover { background: var(--glass-hover); }

/* Retry button for error messages */
.retry-btn {
  margin-top: 8px;
  padding: 6px 14px;
  border-radius: var(--r-sm);
  border: 1px solid var(--gold-border);
  background: var(--gold-soft);
  color: var(--gold-text);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}
.retry-btn:hover {
  background: var(--gold-soft);
  border-color: var(--gold-border);
  filter: brightness(1.08);
}

.msg-act.on { opacity: 1; filter: grayscale(0); }
.suggest-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 14px;
}
.suggest-chips.under-msg { margin-top: 10px; }
.suggest-chip {
  border: 1px solid var(--glass-border);
  background: var(--glass);
  color: var(--t1);
  border-radius: 999px;
  padding: 6px 12px;
  font-size: 12px;
  cursor: pointer;
  transition: background .15s ease, border-color .15s ease;
}
.suggest-chip:hover:not(:disabled) {
  background: var(--glass-hover);
  border-color: var(--gold);
}
.suggest-chip:disabled { opacity: .55; cursor: not-allowed; }
.suggest-chip.ghost { background: transparent; color: var(--t2); }

.hitl-card {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}
.hitl-btn {
  border: 0;
  border-radius: 8px;
  padding: 8px 14px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
}
.hitl-btn:disabled { opacity: .55; cursor: not-allowed; }
.hitl-btn.ok { background: var(--ok); color: #fff; }
.hitl-btn.no { background: var(--layer2); color: var(--t2); border: 1px solid var(--glass-border); }

/* Routing badge */
.routing-badge {
  text-align: center; padding: 8px 16px; margin: 8px auto;
  background: var(--gold-soft); border: 1px solid var(--gold-border-soft);
  border-radius: var(--r-full); font-size: 11px; color: var(--gold-text);
  display: flex; align-items: center; gap: 8px; width: fit-content;
}
.routing-text { font-weight: 500; }
.routing-hint { color: var(--t4); font-size: 10px; opacity: 0.8; }

/* Agent color bands — theme primary for accents */
.msg-row.ai.agent-resume .msg-bub { border-left: 3px solid var(--gold); }
.msg-row.ai.agent-negotiation .msg-bub { border-left: 3px solid var(--gold-text); }
.msg-row.ai.agent-escape .msg-bub { border-left: 3px solid var(--t3); }
.msg-row.ai.agent-consultation .msg-bub { border-left: 3px solid var(--ok); }
.msg-row.ai.agent-general .msg-bub { border-left: 3px solid var(--t4); }

/* Stop generation button */
.chat-stop {
  width: 42px; height: 42px; border-radius: var(--r-md); border: none;
  background: linear-gradient(135deg, var(--danger), color-mix(in srgb, var(--danger) 75%, #000));
  color: #fff; cursor: pointer; display: flex; align-items: center; justify-content: center;
  font-size: 14px; font-weight: 700; transition: all 0.25s var(--spring);
  box-shadow: 0 4px 14px color-mix(in srgb, var(--danger) 35%, transparent);
  animation: pulse-stop 1.5s ease-in-out infinite;
}
.chat-stop:hover { transform: scale(1.06); box-shadow: 0 6px 20px color-mix(in srgb, var(--danger) 45%, transparent); }
.chat-stop:active { transform: scale(0.95); }
@keyframes pulse-stop { 0%, 100% { opacity: 1; } 50% { opacity: 0.8; } }

/* Thinking indicator — friendly phase copy in-thread (tech timeline stays in right panel) */
.typing {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 10px 0;
}
.thinking-av {
  background: var(--gold-soft);
  color: var(--gold-text);
  animation: thinking-pulse 1.6s ease-in-out infinite;
}
.typing-bubble {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  background: var(--layer1);
  border: 1px solid var(--glass-border);
  border-radius: 14px 14px 14px 4px;
  max-width: min(420px, 85%);
}
.typing-dots { display: flex; gap: 5px; flex-shrink: 0; }
.typing-dots b {
  display: block;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--gold-text);
  opacity: 0.55;
  animation: jump 1.4s ease-in-out infinite;
}
.typing-dots b:nth-child(2) { animation-delay: 0.2s; }
.typing-dots b:nth-child(3) { animation-delay: 0.4s; }
.typing-label {
  font-size: 13px;
  color: var(--t3);
  line-height: 1.4;
  animation: fade-in 0.25s ease;
}
@keyframes thinking-pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.72; transform: scale(0.96); }
}
@media (prefers-reduced-motion: reduce) {
  .thinking-av,
  .typing-dots b { animation: none; }
}

/* Quality */
.quality-blocked {
  padding: 8px 22px;
  background: var(--danger-bg);
  border-top: 1px solid rgba(248,113,113,0.15);
  font-size: 12px;
  color: var(--danger);
}
.quality-strip {
  padding: 8px 22px;
  border-top: 1px solid var(--glass-border);
  display: flex; align-items: center; gap: 10px; flex-wrap: wrap;
  font-size: 11px; color: var(--t4); position: relative;
}
.quality-strip.risk-low { border-top-color: var(--ok); background: var(--ok-bg); }
.quality-strip.risk-medium { border-top-color: var(--warn); background: var(--warn-bg); }
.quality-strip.risk-high { border-top-color: var(--danger); background: var(--danger-bg); }
.quality-label { font-weight: 600; }
.quality-detail-btn {
  margin-left: auto; background: none; border: 1px solid var(--glass-border);
  border-radius: var(--r-full); padding: 2px 10px; font-size: 10px;
  color: var(--t3); cursor: pointer; transition: all 0.2s;
}
.quality-detail-btn:hover { background: var(--glass-hover); color: var(--t1); }
.quality-detail-panel {
  width: 100%; margin-top: 8px; padding: 12px 16px;
  background: var(--layer1); border: 1px solid var(--glass-border);
  border-radius: var(--r-sm); display: flex; flex-direction: column; gap: 6px;
}
.qd-row { display: flex; align-items: center; gap: 8px; font-size: 11px; color: var(--t2); }
.qd-label { font-weight: 600; color: var(--t3); min-width: 56px; }
.qd-bar { flex: 1; height: 6px; background: var(--layer2); border-radius: 3px; overflow: hidden; max-width: 120px; }
.qd-fill { display: block; height: 100%; background: var(--ok); border-radius: 3px; transition: width 0.4s; }
.qd-fill.warn { background: var(--warn); }
.qd-issues, .qd-suggestions { margin-top: 4px; }
.qd-issues ul, .qd-suggestions ul { margin: 4px 0 0 16px; padding: 0; font-size: 11px; color: var(--t2); }
.qd-issues li { color: var(--danger); }
.qd-suggestions li { color: var(--gold-text); }

/* Session group labels */
.conv-group-label { font-size: 10px; font-weight: 600; color: var(--t4); padding: 10px 13px 4px; text-transform: uppercase; letter-spacing: 0.8px; }

/* Undo toast */
.undo-toast {
  position: fixed; bottom: 24px; left: 50%; transform: translateX(-50%);
  background: var(--layer1); border: 1px solid var(--glass-border);
  border-radius: var(--r-md); padding: 10px 18px;
  display: flex; align-items: center; gap: 12px;
  font-size: 13px; color: var(--t2); z-index: 200;
  box-shadow: var(--card-hover-shadow);
}
.undo-toast button {
  background: none; border: none; color: var(--gold-text); cursor: pointer;
  font-size: 13px; font-weight: 600; padding: 4px 8px; border-radius: 4px;
}
.undo-toast button:hover { background: var(--gold-soft); }
.toast-enter-active, .toast-leave-active { transition: all 0.3s var(--ease); }
.toast-enter-from, .toast-leave-to { opacity: 0; transform: translateX(-50%) translateY(20px); }

/* Skeleton loading */
.skeleton-msg { display: flex; gap: 14px; padding: 8px 0; }
.skeleton-msg.skel-right { flex-direction: row-reverse; }
.skel-av { width: 30px; height: 30px; border-radius: 50%; background: var(--layer2); animation: skel-pulse 1.5s infinite; }
.skel-bub { padding: 16px 20px; border-radius: var(--r-lg); background: var(--layer1); border: 1px solid var(--glass-border); min-width: 180px; }
.skel-line { height: 12px; border-radius: 6px; background: var(--layer2); margin-bottom: 8px; animation: skel-pulse 1.5s infinite; }
.skel-line.short { width: 60% !important; margin-bottom: 0; }
@keyframes skel-pulse { 0%, 100% { opacity: 0.5; } 50% { opacity: 0.8; } }

@keyframes fade-in { from { opacity: 0; } to { opacity: 1; } }

/* Chat input bar */
.chat-bar { padding: 16px 22px 24px; border-top: 1px solid var(--glass-border); flex-shrink: 0; }
.attached-file {
  display: flex; align-items: center; gap: 8px; padding: 6px 12px; margin-bottom: 8px;
  background: var(--layer2); border-radius: var(--r-sm); font-size: 12px; color: var(--t2);
}
.attached-name { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.attach-hint {
  border: 1px solid var(--glass-border); background: var(--layer1); color: var(--t2);
  border-radius: 6px; font-size: 12px; padding: 2px 6px; outline: none;
}
.attached-file button { background: none; border: none; color: var(--t4); cursor: pointer; font-size: 16px; line-height: 1; }
.perception-status {
  font-size: 12px; color: var(--t3); margin: -2px 0 8px 4px;
}
.perception-status.ok { color: var(--t2); }
.perception-risk { color: #d97706; margin-left: 6px; }
.perception-fields { color: var(--t3); margin-left: 4px; }
.chat-bar-wrap {
  display: flex; align-items: flex-end; gap: 8px;
  background: var(--layer1); border: 1px solid var(--glass-border);
  border-radius: var(--r-lg); padding: 8px 8px 8px 18px;
  transition: border-color 0.3s;
}
.chat-bar-wrap.focused { border-color: var(--gold-border); box-shadow: 0 0 0 3px var(--gold-dim); }
.chat-bar-wrap textarea {
  flex: 1; border: none; background: transparent; color: var(--t1);
  font-family: var(--font); font-size: 14px; line-height: 1.6;
  padding: 8px 0; resize: none; outline: none; min-height: 24px; max-height: 120px;
}
.chat-bar-wrap textarea::placeholder { color: var(--t4); }
.chat-bar-wrap textarea:disabled { opacity: 0.5; }
.bar-btn {
  width: 36px; height: 36px; border-radius: var(--r-sm);
  background: transparent; border: none; color: var(--t3);
  cursor: pointer; display: flex; align-items: center; justify-content: center;
  font-size: 16px; transition: all 0.2s;
}
.bar-btn:hover { background: var(--glass-hover); color: var(--t2); }
.bar-btn:disabled { opacity: 0.4; cursor: not-allowed; }
.bar-btn.listening { color: var(--danger); animation: live 1.5s ease-in-out infinite; }
.chat-send {
  width: 40px; height: 40px; border-radius: var(--r-md);
  border: none; background: var(--gold); color: #fff;
  cursor: pointer; display: flex; align-items: center; justify-content: center;
  font-size: 16px; font-weight: 700; flex-shrink: 0;
  transition: all 0.25s var(--spring);
  box-shadow: 0 4px 14px var(--gold-glow);
}
.chat-send:hover { transform: scale(1.05); box-shadow: 0 6px 22px var(--gold-glow-strong); }
.chat-send:active { transform: scale(0.95); }
.chat-send:disabled { opacity: 0.3; cursor: not-allowed; transform: none; box-shadow: none; background: var(--layer3); color: var(--t4); }

/* Panel */
.panel {
  width: 0; overflow: hidden;
  background: var(--deep); border-left: 1px solid var(--glass-border);
  transition: width 0.35s var(--ease);
}
.panel.show { width: 360px; }
.panel-in { padding: 22px; width: 360px; }
.panel-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px; }
.panel-head h3 { font-size: 14px; font-weight: 600; color: var(--t1); }
.panel-content { font-size: 13px; color: var(--t2); line-height: 1.7; overflow-y: auto; max-height: calc(100vh - 200px); }
.panel-tabs { display: flex; gap: 6px; }
.panel-tab {
  border: 1px solid var(--glass-border); background: transparent; color: var(--t3);
  border-radius: 8px; padding: 4px 10px; font-size: 12px; cursor: pointer;
}
.panel-tab.on { color: var(--t1); border-color: var(--gold-border); background: var(--gold-soft); }
.panel-empty { color: var(--t4); font-size: 13px; padding: 12px 0; }
.artifact-meta { margin-bottom: 12px; }
.artifact-meta h3 { margin: 6px 0 0; font-size: 15px; color: var(--t1); }
.artifact-type {
  display: inline-block; font-size: 11px; padding: 2px 8px; border-radius: 999px;
  background: var(--gold-soft); color: var(--gold-text);
}
.artifact-list { margin-top: 18px; border-top: 1px solid var(--glass-border); padding-top: 12px; }
.artifact-list-title { font-size: 12px; color: var(--t4); margin-bottom: 8px; }
.artifact-list-item {
  display: flex; align-items: center; justify-content: space-between; gap: 8px;
  width: 100%; text-align: left; background: var(--layer2);
  border: 1px solid var(--glass-border); color: var(--t2); border-radius: 8px;
  padding: 8px 10px; margin-bottom: 6px; cursor: pointer; font-size: 12px;
}
.artifact-list-item:hover { border-color: var(--gold-border); }
.artifact-list-item:focus-visible { outline: 2px solid var(--gold-text); outline-offset: 2px; }
.artifact-usage-group { display: inline-flex; gap: 6px; flex: 0 0 auto; }
.artifact-usage {
  flex: 0 0 auto; color: var(--t3); font-size: 11px;
}
.artifact-usage.adopted { color: var(--ok); }
.artifact-chip-row { margin-top: 8px; }
.artifact-chip {
  border: 1px solid var(--gold-border); background: var(--gold-soft);
  color: var(--gold-text); border-radius: 999px; padding: 4px 12px; font-size: 12px; cursor: pointer;
}
.progress-log { margin-bottom: 12px; }
.progress-item { display: flex; gap: 8px; font-size: 12px; padding: 4px 0; color: var(--t3); }
.progress-item.finished .p-status { color: var(--ok); }
.progress-item.failed .p-status { color: var(--danger); }
.sandbox-badge {
  display: inline-block; margin-bottom: 10px; font-size: 11px; padding: 3px 8px;
  border-radius: 6px; background: var(--layer2); color: var(--t3); border: 1px solid var(--glass-border);
}
.save-skill-btn {
  margin-top: 14px; width: 100%; border: 1px solid var(--gold-border);
  background: var(--gold-soft); color: var(--gold-text); border-radius: 10px;
  padding: 8px 12px; cursor: pointer; font-size: 13px;
}
.slash-menu {
  position: absolute; left: 12px; right: 12px; bottom: calc(100% + 8px);
  background: var(--layer1); border: 1px solid var(--glass-border); border-radius: 12px;
  max-height: 220px; overflow-y: auto; z-index: 20;
  box-shadow: var(--card-hover-shadow);
}
.slash-item {
  display: flex; flex-direction: column; gap: 2px; width: 100%; text-align: left;
  background: transparent; border: 0; border-bottom: 1px solid var(--glass-border);
  color: var(--t2); padding: 10px 12px; cursor: pointer;
}
.slash-item strong { color: var(--t1); font-size: 13px; }
.slash-item small { color: var(--t4); font-size: 11px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.slash-empty { padding: 12px; color: var(--t4); font-size: 12px; }
.chat-bar-wrap { position: relative; }
.pack-row {
  display: flex; align-items: center; justify-content: space-between; gap: 12px;
  padding: 12px 0; border-bottom: 1px solid var(--glass-border);
}
.pack-row strong { display: block; color: var(--t1); font-size: 14px; }
.pack-row small { color: var(--t4); font-size: 12px; }

/* Overlay (Profile / Companion / Digital employee) — theme-aware */
.overlay {
  position: fixed; inset: 0; z-index: 100;
  background: var(--overlay-bg);
  backdrop-filter: blur(10px);
  display: flex; align-items: center; justify-content: center;
  padding: 20px;
}
.overlay-panel {
  background: var(--layer1);
  border: 1px solid var(--glass-border);
  border-radius: var(--r-lg);
  width: 420px; max-width: 92vw;
  max-height: 84vh; display: flex; flex-direction: column;
  box-shadow: var(--card-hover-shadow), 0 0 0 1px var(--gold-border-soft);
}
.overlay-panel.wide { width: min(580px, 94vw); }
.overlay-header {
  display: flex; align-items: flex-start; justify-content: space-between;
  gap: 16px;
  padding: 20px 22px 16px; border-bottom: 1px solid var(--glass-border);
}
.overlay-header h2 { font-size: 16px; font-weight: 600; color: var(--t1); }
.overlay-title { display: inline-flex; align-items: center; gap: 8px; }
.overlay-title .wp-icon { color: var(--gold-text); }
.overlay-title-block { min-width: 0; flex: 1; }
.overlay-title-block h2 { margin: 0 0 4px; font-size: 17px; }
.overlay-title-block p {
  margin: 0;
  font-size: 12px;
  color: var(--t3);
  line-height: 1.45;
}
.overlay-badge {
  display: inline-flex;
  align-items: center;
  padding: 3px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  margin-bottom: 8px;
  letter-spacing: 0.02em;
}
.overlay-badge.companion {
  color: var(--gold-text);
  background: var(--gold-soft);
  border: 1px solid var(--gold-border);
}
.overlay-badge.employee {
  color: var(--gold-text);
  background: var(--gold-soft);
  border: 1px solid var(--gold-border);
}
.overlay-close {
  width: 34px;
  height: 34px;
  flex-shrink: 0;
  border-radius: 10px;
  border: 1px solid var(--glass-border);
  background: var(--layer2);
  color: var(--t2);
  font-size: 22px;
  line-height: 1;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: background .15s ease, border-color .15s ease, color .15s ease;
}
.overlay-close:hover {
  background: var(--danger-bg);
  border-color: var(--danger);
  color: var(--danger);
}
.overlay-body { padding: 20px 22px; flex: 1; overflow-y: auto; }
.overlay-loading, .overlay-empty { text-align: center; color: var(--t3); font-size: 13px; padding: 20px; }
.profile-content { display: flex; flex-direction: column; gap: 16px; }
.profile-field { display: flex; flex-direction: column; gap: 4px; }
.field-label { font-size: 11px; color: var(--t3); font-weight: 600; text-transform: uppercase; letter-spacing: 0.5px; }
.field-value { font-size: 14px; color: var(--t1); }
.field-tags { display: flex; flex-wrap: wrap; gap: 6px; }
.field-tag { padding: 4px 12px; border-radius: var(--r-full); background: var(--gold-soft); color: var(--gold-text); font-size: 12px; }
.field-empty { font-size: 12px; color: var(--t4); }
.overlay-footer { padding: 14px 22px 18px; border-top: 1px solid var(--glass-border); }
.team-footer { display: flex; justify-content: flex-end; gap: 10px; }
.clear-btn {
  padding: 8px 16px; border-radius: var(--r-sm);
  background: var(--danger-bg); border: 1px solid color-mix(in srgb, var(--danger) 28%, transparent);
  color: var(--danger); font-size: 12px; cursor: pointer; transition: all 0.2s;
}
.clear-btn:hover { background: color-mix(in srgb, var(--danger) 18%, transparent); }
.clear-btn:disabled { opacity: 0.5; cursor: not-allowed; }

.team-form { display: flex; flex-direction: column; gap: 14px; }
.team-field { display: flex; flex-direction: column; gap: 6px; }
.team-input {
  width: 100%;
  border: 1px solid var(--glass-border);
  background: var(--layer2);
  color: var(--t1);
  border-radius: 12px;
  padding: 10px 12px;
  font-size: 13px;
  outline: none;
  transition: border-color .15s ease, box-shadow .15s ease;
}
.team-input::placeholder { color: var(--t4); }
.team-input:focus {
  border-color: var(--gold-border);
  box-shadow: 0 0 0 3px var(--gold-dim);
}
.team-textarea { resize: vertical; min-height: 96px; line-height: 1.55; }
.team-toast {
  margin-top: 14px;
  padding: 10px 12px;
  border-radius: 10px;
  font-size: 12px;
  text-align: left;
}
.team-toast.ok {
  color: var(--ok);
  background: var(--ok-bg);
  border: 1px solid var(--gold-border-soft);
}
.team-toast.err {
  color: var(--danger);
  background: var(--danger-bg);
  border: 1px solid color-mix(in srgb, var(--danger) 28%, transparent);
}
.btn-primary {
  border: none;
  background: var(--gold-grad);
  color: #fff;
  font-weight: 600;
  font-size: 13px;
  border-radius: 12px;
  padding: 10px 16px;
  cursor: pointer;
  box-shadow: 0 8px 20px var(--gold-glow);
}
.btn-primary:disabled { opacity: .55; cursor: not-allowed; box-shadow: none; }
.btn-ghost {
  border: 1px solid var(--glass-border);
  background: transparent;
  color: var(--t2);
  font-size: 13px;
  border-radius: 12px;
  padding: 10px 14px;
  cursor: pointer;
}
.btn-ghost:hover { background: var(--glass-hover); color: var(--t1); }
.btn-mini {
  border: 1px solid var(--glass-border);
  background: var(--layer2);
  color: var(--t1);
  font-size: 12px;
  border-radius: 999px;
  padding: 6px 12px;
  cursor: pointer;
}
.btn-mini.primary {
  border-color: var(--gold-border);
  background: var(--gold-soft);
  color: var(--gold-text);
}
.btn-mini.ghost { color: var(--t3); background: transparent; }
.btn-mini:hover { border-color: var(--gold-border); }

.tpl-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}
.tpl-card {
  text-align: left;
  border: 1px solid var(--glass-border);
  background: var(--layer2);
  border-radius: 14px;
  padding: 14px;
  cursor: pointer;
  transition: border-color .15s ease, background .15s ease, transform .15s ease;
}
.tpl-card:hover {
  border-color: var(--gold-border);
  background: var(--gold-soft);
  transform: translateY(-1px);
}
.tpl-card:disabled { opacity: .55; cursor: not-allowed; transform: none; }
.tpl-card strong {
  display: block;
  color: var(--t1);
  font-size: 13px;
  margin-bottom: 6px;
}
.tpl-card span {
  display: block;
  color: var(--t3);
  font-size: 11px;
  line-height: 1.45;
}

.de-card {
  border: 1px solid var(--glass-border);
  background: var(--layer2);
  border-radius: 14px;
  padding: 14px;
  margin-bottom: 10px;
}
.de-card.active {
  border-color: var(--gold-border);
  background: var(--gold-soft);
}
.de-card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.de-card-head strong { color: var(--t1); font-size: 14px; }
.de-current {
  display: inline-flex;
  margin-left: 8px;
  padding: 2px 7px;
  border-radius: 999px;
  font-size: 10px;
  font-weight: 600;
  color: var(--gold-text);
  background: var(--gold-soft);
  vertical-align: middle;
}
.de-meta { font-size: 12px; color: var(--t3); }
.de-persona-preview {
  margin: 0 0 12px;
  font-size: 12px;
  color: var(--t2);
  line-height: 1.55;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.de-card-actions { display: flex; flex-wrap: wrap; gap: 6px; }
.de-section-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--t2);
  margin-bottom: 10px;
}
.de-preset-row { margin: 14px 0 4px; }
.de-editor-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 16px;
}
.de-persona { min-height: 140px; }

@media (max-width: 720px) {
  .tpl-grid { grid-template-columns: 1fr; }
}

/* Markdown content in messages */
.msg-bub :deep(p) { margin: 0 0 8px; }
.msg-bub :deep(p:last-child) { margin: 0; }
.msg-bub :deep(h1), .msg-bub :deep(h2), .msg-bub :deep(h3), .msg-bub :deep(h4) {
  margin: 12px 0 6px; font-weight: 600; color: var(--t1); line-height: 1.4;
}
.msg-bub :deep(h1:first-child), .msg-bub :deep(h2:first-child), .msg-bub :deep(h3:first-child), .msg-bub :deep(h4:first-child) {
  margin-top: 0;
}
.msg-bub :deep(h1) { font-size: 1.15em; }
.msg-bub :deep(h2) { font-size: 1.08em; }
.msg-bub :deep(h3), .msg-bub :deep(h4) { font-size: 1em; }
.msg-bub :deep(strong), .msg-bub :deep(b) { color: var(--t1); font-weight: 600; }
.msg-bub :deep(em) { color: var(--t2); font-style: italic; }
.msg-bub :deep(code) { font-family: var(--mono); font-size: 12px; padding: 2px 6px; background: var(--layer2); border-radius: 4px; }
.msg-bub :deep(pre) { background: var(--layer2); border-radius: var(--r-sm); padding: 12px; overflow-x: auto; margin: 8px 0; }
.msg-bub :deep(ul), .msg-bub :deep(ol) { padding-left: 18px; margin: 6px 0; }
.msg-bub :deep(li) { margin-bottom: 4px; }

.tb-btn {
  width: 34px; height: 34px; border-radius: var(--r-sm);
  border: none; background: transparent; color: var(--t3);
  cursor: pointer; display: flex; align-items: center; justify-content: center;
  font-size: 15px; transition: all 0.2s var(--ease);
}
.tb-btn:hover { background: var(--glass-hover); color: var(--t2); }

@media (max-width: 768px) {
  .sidebar { display: none; }
  .back-mobile { display: flex; }
  .panel.show { width: 100%; position: absolute; right: 0; top: 0; bottom: 0; z-index: 10; }
}
</style>
