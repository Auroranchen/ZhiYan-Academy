<template>
  <PageLayout title="学习激励中心" description="积分、徽章与排行榜，坚持打卡赢取更多奖励。">
    <template #actions>
      <el-button
        type="primary"
        :disabled="profile.checkedToday"
        :loading="checking"
        @click="checkin"
      >
        {{ profile.checkedToday ? '今日已打卡' : '每日打卡' }}
      </el-button>
    </template>

    <div class="hero">
      <div class="hero__points">
        <p class="hero__value">{{ profile.totalPoints || 0 }}</p>
        <p class="hero__label">我的积分</p>
      </div>

      <div class="hero__level">
        <div class="hero__level-head">
          <span class="hero__badge">Lv.{{ profile.level || 1 }}</span>
          <span class="hero__gap">距下一级还需 {{ profile.nextLevelGap || 0 }} 分</span>
        </div>

        <el-progress :percentage="levelPercent" :stroke-width="12" :show-text="false" color="#fff" />

        <div class="hero__stats">
          <span><Icon icon="ph:fire-duotone" /> 连续打卡 {{ profile.continuousDays || 0 }} 天</span>
          <span><Icon icon="ph:medal-duotone" /> 徽章 {{ profile.ownedBadgeCount || 0 }} 枚</span>
          <span><Icon icon="ph:ranking-duotone" /> 全校第 {{ profile.rank || '—' }} 名</span>
        </div>
      </div>
    </div>

    <el-row :gutter="16">
      <el-col :xs="24" :lg="14">
        <SectionCard title="徽章墙" :count="profile.badges?.length || 0">
          <div class="badges">
            <div
              v-for="badge in profile.badges || []"
              :key="badge.code"
              class="badge"
              :class="{ 'is-locked': !badge.owned }"
            >
              <Icon :icon="badge.icon || 'ph:medal-duotone'" :width="32" :height="32" class="badge__icon" />
              <p class="badge__name">{{ badge.name }}</p>
              <p class="badge__desc">{{ badge.description }}</p>
              <el-tag :type="badge.owned ? 'success' : 'info'" size="small" disable-transitions>
                {{ badge.owned ? '已获得' : '未解锁' }}
              </el-tag>
            </div>
          </div>
        </SectionCard>

        <SectionCard title="最近积分记录">
          <el-empty
            v-if="!(profile.recentLogs || []).length"
            description="暂无积分记录"
            :image-size="60"
          />
          <el-timeline v-else>
            <el-timeline-item
              v-for="(log, index) in profile.recentLogs"
              :key="index"
              :type="log.points >= 0 ? 'success' : 'danger'"
              :timestamp="formatDate(log.createTime, 'minute')"
            >
              <div class="log">
                <span>{{ log.reason }}</span>
                <span class="log__points" :class="{ 'is-gain': log.points >= 0 }">
                  {{ log.points >= 0 ? '+' : '' }}{{ log.points }}
                </span>
              </div>
            </el-timeline-item>
          </el-timeline>
        </SectionCard>
      </el-col>

      <el-col :xs="24" :lg="10">
        <SectionCard title="积分排行榜">
          <template #action>
            <el-radio-group v-model="scope" size="small">
              <el-radio-button value="all">全校</el-radio-button>
              <el-radio-button value="class">本班</el-radio-button>
            </el-radio-group>
          </template>

          <div v-loading="rankLoading" class="ranks">
            <el-empty v-if="!leaderboard.length" description="暂无数据" :image-size="60" />
            <div
              v-for="(row, index) in leaderboard"
              :key="row.studentid"
              class="rank"
              :class="MEDAL_CLASS[index]"
            >
              <div class="rank__no">
                <Icon v-if="index < 3" :icon="MEDAL_ICON[index]" :width="22" :height="22" />
                <span v-else>{{ index + 1 }}</span>
              </div>
              <div class="rank__name">
                {{ row.studentname }}
                <small>{{ row.classname || '' }}</small>
              </div>
              <div class="rank__meta">
                <span class="rank__points">{{ row.totalPoints }} 分</span>
                <el-tag size="small" effect="plain" disable-transitions>Lv.{{ row.level }}</el-tag>
              </div>
            </div>
          </div>
        </SectionCard>
      </el-col>
    </el-row>
  </PageLayout>
</template>

<script setup>
/** 学习激励中心：积分、徽章、排行榜 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Icon } from '@iconify/vue'
import { PageLayout, SectionCard } from '@/components/ui'
import { dailyCheckin, getGamificationProfile, getLeaderboard } from '@/api/gamification'
import { formatDate } from '@/utils/format'

/** 后端等级公式是「每满 100 分升一级」，所以剩余分差可直接换算成百分比 */
const POINTS_PER_LEVEL = 100

const MEDAL_ICON = ['ph:trophy-duotone', 'ph:medal-duotone', 'ph:medal-duotone']
const MEDAL_CLASS = ['is-gold', 'is-silver', 'is-bronze']

const profile = reactive({})
const leaderboard = ref([])
const scope = ref('all')
const checking = ref(false)
const rankLoading = ref(false)

const levelPercent = computed(() => {
  const gap = Number(profile.nextLevelGap) || 0
  return Math.max(0, Math.min(100, POINTS_PER_LEVEL - gap))
})

const loadProfile = async () => {
  const res = await getGamificationProfile().catch(() => null)
  Object.assign(profile, res?.data || {})
}

const loadLeaderboard = async () => {
  rankLoading.value = true
  try {
    const res = await getLeaderboard(scope.value).catch(() => null)
    leaderboard.value = res?.data || []
  } finally {
    rankLoading.value = false
  }
}

watch(scope, loadLeaderboard)

onMounted(() => {
  loadProfile()
  loadLeaderboard()
})

const checkin = async () => {
  checking.value = true
  try {
    const res = await dailyCheckin()
    ElMessage.success(res?.msg || '打卡成功')
    await Promise.all([loadProfile(), loadLeaderboard()])
  } catch {
    // 失败提示由 http 拦截器统一处理
  } finally {
    checking.value = false
  }
}
</script>

<style scoped>
.hero {
  display: flex;
  align-items: center;
  gap: var(--zy-space-8);
  padding: var(--zy-space-6);
  margin-bottom: var(--zy-space-4);
  color: #fff;
  background: linear-gradient(135deg, var(--zy-palette-brand-600), var(--zy-palette-brand-400));
  border-radius: var(--zy-radius-lg);
}

.hero__points {
  text-align: center;
}

.hero__value {
  margin: 0;
  font-size: 44px;
  font-weight: var(--zy-font-weight-bold);
  line-height: 1;
  font-variant-numeric: tabular-nums;
}

.hero__label {
  margin: var(--zy-space-2) 0 0;
  font-size: var(--zy-font-size-sm);
  opacity: 0.9;
}

.hero__level {
  flex: 1;
  min-width: 0;
}

.hero__level-head {
  display: flex;
  align-items: center;
  gap: var(--zy-space-3);
  margin-bottom: var(--zy-space-2);
}

.hero__badge {
  padding: 2px 12px;
  font-weight: var(--zy-font-weight-bold);
  background: rgb(255 255 255 / 25%);
  border-radius: var(--zy-radius-full);
}

.hero__gap {
  font-size: var(--zy-font-size-sm);
  opacity: 0.9;
}

.hero__stats {
  display: flex;
  flex-wrap: wrap;
  gap: var(--zy-space-5);
  margin-top: var(--zy-space-3);
  font-size: var(--zy-font-size-sm);
}

.hero__stats span {
  display: inline-flex;
  align-items: center;
  gap: var(--zy-space-1);
}

/* ===== 徽章墙 ===== */
.badges {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: var(--zy-space-3);
}

.badge {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--zy-space-1);
  padding: var(--zy-space-4) var(--zy-space-2);
  text-align: center;
  border: 1px solid var(--zy-border-default);
  border-radius: var(--zy-radius-md);
}

.badge.is-locked {
  filter: grayscale(1);
  opacity: 0.5;
}

.badge__icon {
  color: var(--zy-warning-base);
}

.badge__name {
  margin: 0;
  font-size: var(--zy-font-size-sm);
  font-weight: var(--zy-font-weight-semibold);
  color: var(--zy-text-primary);
}

.badge__desc {
  margin: 0;
  min-height: 32px;
  font-size: var(--zy-font-size-xs);
  line-height: var(--zy-line-height-normal);
  color: var(--zy-text-tertiary);
}

/* ===== 积分流水 ===== */
.log {
  display: flex;
  align-items: center;
  gap: var(--zy-space-3);
  font-size: var(--zy-font-size-sm);
  color: var(--zy-text-secondary);
}

.log__points {
  font-weight: var(--zy-font-weight-bold);
  color: var(--zy-danger-base);
}

.log__points.is-gain {
  color: var(--zy-success-base);
}

/* ===== 排行榜 ===== */
.ranks {
  max-height: 520px;
  overflow-y: auto;
}

.rank {
  display: flex;
  align-items: center;
  gap: var(--zy-space-3);
  padding: var(--zy-space-2) var(--zy-space-3);
  margin-bottom: var(--zy-space-1);
  background: var(--zy-bg-sunken);
  border-radius: var(--zy-radius-md);
}

.rank.is-gold {
  background: var(--zy-warning-soft);
}

.rank.is-silver,
.rank.is-bronze {
  background: var(--zy-bg-hover);
}

.rank__no {
  width: 28px;
  text-align: center;
  font-weight: var(--zy-font-weight-bold);
  color: var(--zy-text-tertiary);
}

.rank.is-gold .rank__no {
  color: var(--zy-warning-base);
}

.rank__name {
  flex: 1;
  min-width: 0;
  font-size: var(--zy-font-size-sm);
  color: var(--zy-text-primary);
}

.rank__name small {
  margin-left: var(--zy-space-1);
  font-size: var(--zy-font-size-xs);
  color: var(--zy-text-tertiary);
}

.rank__meta {
  display: flex;
  align-items: center;
  gap: var(--zy-space-2);
}

.rank__points {
  font-weight: var(--zy-font-weight-bold);
  color: var(--zy-brand-base);
  font-variant-numeric: tabular-nums;
}

@media screen and (max-width: 768px) {
  .hero {
    flex-direction: column;
    gap: var(--zy-space-4);
    text-align: center;
  }

  .hero__level {
    width: 100%;
  }

  .hero__level-head,
  .hero__stats {
    justify-content: center;
  }
}
</style>
