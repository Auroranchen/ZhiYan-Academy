/**
 * 多智能体协作流水线
 *
 * 服务端按交接依赖把 7 个内容智能体分波并行调用（见 AgentPipelineService），
 * 实测一次完整流水线约 1 分钟，因此：
 * - 都支持传入 AbortSignal，用户点「停止」时要能真正掐断在途请求；
 * - 进度通过 SSE 推送，同一波内会有多个智能体同时处于「运行中」；
 * - 页面刷新后靠 latest 接口重新挂载。
 */
import http from '@/utils/http'

/** 单个智能体的调用，signal 用于中途取消 */
const callAgent = (url, data, signal) => http.post(url, data, { signal })

/* ---------- 流水线 ---------- */

export const startPipeline = (data, signal) =>
  http.post('/agent/pipeline/start', data, { signal, suppressMessage: true })

/** 取消是尽力而为，失败也不该打断前端的停止流程 */
export const cancelPipeline = (runId) =>
  http.post(`/agent/pipeline/cancel/${runId}`, {}, { suppressMessage: true })

/** 刷新页面后恢复进行中的流水线；没有在跑的属正常情况 */
export const getLatestPipeline = () =>
  http.get('/agent/pipeline/latest', { suppressMessage: true })

/** SSE 进度流的地址，由页面用 fetch 自行读取 */
export const pipelineStreamUrl = (runId) => `/ZhiYanAcademy/agent/pipeline/stream/${runId}`

/* ---------- 各智能体 ---------- */

export const diagnose = (data, signal) => callAgent('/agent/diagnose', data, signal)
export const diagnoseClass = (data, signal) => callAgent('/agent/diagnose-class', data, signal)
export const planLearning = (data, signal) => callAgent('/agent/plan', data, signal)
export const buildResource = (data, signal) => callAgent('/agent/resource', data, signal)
export const buildMindMap = (data, signal) => callAgent('/agent/mindmap', data, signal)
export const buildQuiz = (data, signal) => callAgent('/agent/quiz', data, signal)
export const buildReading = (data, signal) => callAgent('/agent/reading', data, signal)
export const buildCodeCase = (data, signal) => callAgent('/agent/codecase', data, signal)
export const assemblePack = (data, signal) => callAgent('/agent/assemble', data, signal)

/** 独立入口：只生成讲解动画分镜，不跑整条流水线 */
export const buildExplainerScenes = (data, signal) => callAgent('/agent/explainer', data, signal)

/* ---------- 资源包库 ---------- */

export const listAgentResources = () => http.get('/agent/library')
export const getAgentResource = (id) => http.get(`/agent/library/${id}`)
export const deleteAgentResource = (id) => http.delete(`/agent/library/${id}`)

/** 把资源包推送给课程下的全部学生 */
export const pushAgentResource = (id) => http.post(`/agent/push/${id}`)
