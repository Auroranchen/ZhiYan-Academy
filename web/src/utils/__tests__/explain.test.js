// stripHtml 通过 DOMPurify 清洗题干，需要真实 DOM
// @vitest-environment jsdom
import { describe, expect, it } from 'vitest'
import {
  correctnessKnown,
  formatAnswer,
  generateCodeExplanation,
  generateKeyPoints,
  getStudentScore,
  isCodeQuestion,
  isExplainRestricted,
  isFullScore,
  isOptionCorrect,
  isOptionWrong,
  parseAnswerLetters
} from '@/utils/explain'

const letters = (answer) => [...parseAnswerLetters(answer)].sort().join('')

describe('答案解析', () => {
  it('识别历史数据里的五种答案形态', () => {
    expect(letters(2)).toBe('C')
    expect(letters('2')).toBe('C')
    expect(letters([0, 1])).toBe('AB')
    expect(letters('[0,2]')).toBe('AC')
    expect(letters('A,C')).toBe('AC')
    expect(letters('AC')).toBe('AC')
  })

  it('下标 0 不能当成空答案', () => {
    expect(letters(0)).toBe('A')
  })

  it('无答案返回空集合', () => {
    expect(parseAnswerLetters(null).size).toBe(0)
    expect(parseAnswerLetters('').size).toBe(0)
    expect(parseAnswerLetters(undefined).size).toBe(0)
  })

  it('多位数字不按选项下标解释', () => {
    expect(letters('12')).toBe('')
  })
})

describe('选项对错标注', () => {
  const question = { correctAnswer: '[0,2]', studentAnswer: '[0,1]' }

  it('标准答案里的选项标记为正确', () => {
    expect(isOptionCorrect(0, question)).toBe(true)
    expect(isOptionCorrect(2, question)).toBe(true)
    expect(isOptionCorrect(1, question)).toBe(false)
  })

  it('只有学生选错的选项才标红，选对的不算错', () => {
    expect(isOptionWrong(1, question)).toBe(true)
    expect(isOptionWrong(0, question)).toBe(false)
    expect(isOptionWrong(3, question)).toBe(false)
  })

  it('题库保护开启时不泄露任何答案标注', () => {
    const restricted = { ...question, explainPolicy: 'restricted' }
    expect(isExplainRestricted(restricted)).toBe(true)
    expect(correctnessKnown(restricted)).toBe(false)
    expect(isOptionCorrect(0, restricted)).toBe(false)
    expect(isOptionWrong(1, restricted)).toBe(false)
  })
})

describe('得分展示', () => {
  it('选择题按答案是否一致给满分或零分', () => {
    const right = { questionType: 'single', correctAnswer: '1', studentAnswer: '1', score: 5 }
    const wrong = { questionType: 'single', correctAnswer: '1', studentAnswer: '2', score: 5 }
    expect(getStudentScore(right)).toBe(5)
    expect(getStudentScore(wrong)).toBe(0)
    expect(isFullScore(right)).toBe(true)
  })

  it('编程题用后端下发的实际得分，不做字符串比较', () => {
    const code = { questionType: 'prog', referenceCode: 'int main(){}', score: 10, studentScore: 8 }
    expect(isCodeQuestion(code)).toBe(true)
    expect(getStudentScore(code)).toBe(8)
    expect(isFullScore(code)).toBe(false)
    expect(isFullScore({ ...code, studentScore: 10 })).toBe(true)
  })

  it('无法判定正误时显示占位符而不是 0 分', () => {
    expect(getStudentScore({ questionType: 'short', score: 10 })).toBe('—')
  })
})

describe('答案文案', () => {
  it('选择题输出排序后的字母', () => {
    expect(formatAnswer('[2,0]', 'multi')).toBe('A, C')
  })

  it('判断题输出中文', () => {
    expect(formatAnswer('true', 'judge')).toBe('正确')
    expect(formatAnswer(false, 'judge')).toBe('错误')
  })

  it('未作答给出提示文案', () => {
    expect(formatAnswer('', 'single')).toBe('未作答')
    expect(formatAnswer(null, 'short')).toBe('未作答')
  })
})

describe('知识点推断', () => {
  it('优先命中更具体的关键词', () => {
    // 关键词表里「树」在「二叉树」之后，否则二叉树的题会退化成泛泛的「树」
    expect(generateKeyPoints({ questionTitle: '二叉树的前序遍历' })).toContain('二叉树')
    expect(generateKeyPoints({ questionTitle: '红黑树属于什么结构' })).toContain('树的结构')
  })

  it('没有命中关键词时截取题干', () => {
    expect(generateKeyPoints({ questionTitle: '请说明本课程的考核方式' })).toContain('本题考查')
  })

  it('题干过短时回退到通用文案', () => {
    expect(generateKeyPoints({ questionTitle: '为什么' })).toBe('本题考查基础知识的理解与应用')
  })
})

describe('代码讲解', () => {
  it('没有参考代码时不生成讲解', () => {
    expect(generateCodeExplanation({ referenceCode: '   ' })).toBe('')
  })

  it('识别双重循环与哈希表思路', () => {
    const text = generateCodeExplanation({
      referenceCode: 'for (int i=0;i<n;i++) { for (int j=0;j<n;j++) {} }'
    })
    expect(text).toContain('双重循环')
  })

  it('无法识别模式时给出通用讲解', () => {
    const text = generateCodeExplanation({ referenceCode: 'int x = 1;' })
    expect(text).toContain('仔细阅读这段参考代码')
    expect(text).toContain('加深理解')
  })
})
