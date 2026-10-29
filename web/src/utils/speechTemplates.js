/**
 * 话术模板库 SpeechTemplates
 * 特级AI教师讲题风格：引导思考、启发式教学、关注方法论
 * 支持变量插值和连续去重
 */

// 模板数据：每类至少 8 个，融入特级教师讲题技巧
const TEMPLATES = {
  intro: [
    '看第{index}题，考点比较集中。',
    '第{index}题，先看题干再给条件。',
    '第{index}题来了，这道题考试很爱出，大家认真听。',
    '我们看第{index}题，先想想这道题考的是什么。',
    '第{index}题，这是一个重要的知识点，我们慢慢来。',
    '好的，第{index}题，我带大家理清思路。',
    '来，第{index}题，跟着老师的思路一步一步走。',
    '第{index}题，这道题很有代表性，我们重点讲。',
    '同学们注意，第{index}题是高频考点，仔细听。',
    '第{index}题，我们先审题，审题是解题的第一步。',
  ],
  questionLead: [
    '这是一道{questionType}题，我们先把题目读懂。',
    '先看题目，这是{questionType}题，注意关键词。',
    '题目是这样的，{questionType}题，我们先找出题目的核心问题。',
    '我们先把题目读一遍，这道{questionType}题说的是。',
    '来看看这道{questionType}题，审题时要抓住重点。',
    '好，这道{questionType}题，我们先分析题目在问什么。',
    '注意看题目哦，这是一道{questionType}题，别漏看条件。',
    '先审题，这道{questionType}题的关键在于。',
  ],
  keypointLead: [
    '这道题背后的核心知识点是这样的。',
    '我们先回顾一下相关知识点，这是解题的基础。',
    '要做对这道题，必须掌握这个知识点。',
    '这里有个重要的知识点，考试必考，大家记好。',
    '这道题考查的核心知识点是。',
    '让我先带大家梳理一下这个知识点，理解了才能举一反三。',
    '掌握了这个知识点，类似的题就都会做了。',
    '这个知识点很重要，我们往年考试也经常考到。',
  ],
  errorLead: [
    '我们来看看这道题容易错在哪里，这是个常见的坑。',
    '很多同学在这里会犯一个小错误，我带你们避开。',
    '注意哦，这道题有个容易踩的坑，记住了下次就不会再错。',
    '来分析一下错误的原因，知道为什么错比知道正确答案更重要。',
    '这道题的易错点在于，很多同学都会忽略这个细节。',
    '别担心，错了正好是学习的机会，我们看看问题出在哪。',
    '这个错误其实很常见，关键是要理解背后的原理。',
    '让我来帮你分析一下，下次遇到类似的题就能避开了。',
  ],
  stepLead: [
    '第{index}步，我们这样思考。',
    '接下来第{index}步，跟着我的思路走。',
    '然后第{index}步，这里是关键。',
    '好，第{index}步，注意这个推理过程。',
    '我们继续，第{index}步是解题的核心。',
    '下一步，第{index}步，想想为什么这样做。',
    '第{index}步，这个方法很实用，记住了。',
    '第{index}步，我们用排除法来分析。',
  ],
  transition: [
    '好，这道题我们就讲到这里，记得回去复习这个知识点。',
    '这道题分析完了，建议大家把解题思路记下来。',
    '好的，这道题就这样，下一道。',
    '总结一下这道题的解题方法，然后我们继续。',
    '这道题讲完了，希望大家能举一反三。',
    '好，我们接着往下看，保持思考。',
    '这道题就到这里，准备好了吗？',
    '分析完毕，记住解题的关键步骤，我们继续。',
  ],
  encouragement: [
    '做得不错，说明你对这个知识点掌握得很好。',
    '很好，完全正确，继续保持。',
    '答对了，看来你的基础很扎实。',
    '没错，就是这样，这个方法要记牢。',
    '正确，你真棒，这种题型以后都能拿分。',
    '对了，这道题你理解得很到位。',
    '完全正确，说明你对这个概念已经很熟悉了。',
    '答对了，基础很扎实，继续加油。',
  ],
  comfort: [
    '没关系，这道题确实有难度，关键是搞懂解题思路。',
    '别灰心，错了才知道哪里需要加强，这就是进步。',
    '这道题做错了也没关系，记住解题方法下次就能做对。',
    '不要紧，很多同学都会在这里出错，重要的是学会方法。',
    '错了不可怕，重要的是搞懂为什么错，下次就能避开。',
    '别担心，我们一起把这道题弄明白，下次肯定能做对。',
    '这道题有点绕，做错很正常，关键是掌握解题套路。',
    '没事的，学习就是一个不断纠错的过程，我们一起加油。',
  ],
  // 上下文感知：答错情境的开场白，先安抚情绪再引导分析
  wrongIntro: [
    '第{index}题没答对没关系，我们一起分析错在哪。',
    '第{index}题错了很正常，这道题有点绕，听我慢慢讲。',
    '来看第{index}题，先别急，我们找找刚才错在哪一步。',
    '第{index}题答错了不要紧，跟着我把思路重新理一遍。',
    '第{index}题错得有价值，我们来分析背后的知识漏洞。',
    '别灰心，第{index}题我们错在细节上，一起来看。',
    '第{index}题没做对，说明这个考点还没吃透，我们仔细分析。',
    '第{index}题有个容易错的坑，我们看看是不是踩中了。',
  ],
}

// 题目类型中文映射
const QUESTION_TYPE_MAP = {
  single: '单选',
  multi: '多选',
  judge: '判断',
  blank: '填空',
  short: '简答',
}

/**
 * 变量插值：将 {key} 替换为 context 中对应的值
 */
function interpolate(template, context) {
  return template.replace(/\{(\w+)\}/g, (match, key) => {
    if (key === 'questionType' && context[key]) {
      return QUESTION_TYPE_MAP[context[key]] || context[key]
    }
    return context[key] !== undefined ? String(context[key]) : match
  })
}

/**
 * 创建话术模板库实例
 */
export function createSpeechTemplates() {
  // 去重记录：记录每个类别上次使用的索引
  const lastUsed = new Map()

  /**
   * 从指定类别中随机选择一个模板（避免连续重复）
   */
  function pick(category, context = {}) {
    const templates = TEMPLATES[category]
    if (!templates || templates.length === 0) return ''

    const lastIndex = lastUsed.get(category)
    let index

    if (templates.length === 1) {
      index = 0
    } else {
      do {
        index = Math.floor(Math.random() * templates.length)
      } while (index === lastIndex)
    }

    lastUsed.set(category, index)
    return interpolate(templates[index], context)
  }

  return {
    getIntro(context) {
      // 上下文感知：答错的题目用「安抚 + 引导分析」语气的开场白
      if (context && context.isCorrect === false) {
        return pick('wrongIntro', context)
      }
      return pick('intro', context)
    },
    getQuestionLead(context) {
      return pick('questionLead', context)
    },
    getKeypointLead(context) {
      return pick('keypointLead', context)
    },
    getErrorLead(context) {
      return pick('errorLead', context)
    },
    getStepLead(context) {
      return pick('stepLead', context)
    },
    getTransition(context) {
      return pick('transition', context)
    },
    getEncouragement() {
      return pick('encouragement')
    },
    getComfort() {
      return pick('comfort')
    },
    reset() {
      lastUsed.clear()
    },
    _getTemplates() {
      return TEMPLATES
    },
    _getQuestionTypeMap() {
      return QUESTION_TYPE_MAP
    },
  }
}
