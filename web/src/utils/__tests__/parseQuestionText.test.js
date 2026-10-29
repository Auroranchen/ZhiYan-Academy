import { describe, expect, it } from 'vitest'
import { parseQuestionText } from '@/utils/exam'

/**
 * 教师从 Word / PDF / 剪贴板导入试卷时全靠这个解析器。
 * 它是 180 行正则驱动的状态机，识别错了教师往往要手工改一整套题，
 * 因此这里覆盖各种常见的书写变体。
 */
const parse = (text) => parseQuestionText(text)
const first = (text) => parse(text).questions[0]

describe('单选题', () => {
  it('标准格式：题号、选项各占一行、单独答案行', () => {
    const q = first(`1. 以下哪个不是 Java 的基本数据类型？
A. int
B. String
C. boolean
D. double
答案：B`)

    expect(q.type).toBe('single')
    expect(q.title).toContain('基本数据类型')
    expect(q.options).toEqual(['int', 'String', 'boolean', 'double'])
    expect(q.answer).toBe(1)
  })

  it('选项挤在一行也能拆开', () => {
    const q = first(`1、下列哪个是引用类型？
A、int  B、String  C、char  D、boolean
答案：B`)

    expect(q.options).toEqual(['int', 'String', 'char', 'boolean'])
    expect(q.answer).toBe(1)
  })

  it('选项内容含英文时不会被吞掉', () => {
    // 编程课的选项大量是 int / String / List 这类英文，切分时不能把字母当成新选项
    expect(first('1. 哪个是集合？\nA. List  B. Map  C. int\n答案：A').options).toEqual([
      'List',
      'Map',
      'int'
    ])
  })

  it('选项跨两行并排书写也能续上', () => {
    const q = first(`1. 题干
A. 甲  B. 乙
C. 丙  D. 丁
答案：C`)

    expect(q.options).toEqual(['甲', '乙', '丙', '丁'])
    expect(q.answer).toBe(2)
  })

  it('题干与选项挤在同一行时会拆开，选项不会留在标题里', () => {
    const q = first('1. 下列哪个是关键字？ A. class  B. myVar  C. 123\n答案：A')

    expect(q.title).toBe('下列哪个是关键字？')
    expect(q.options).toEqual(['class', 'myVar', '123'])
    expect(q.answer).toBe(0)
  })

  it('题干里出现孤立的字母加标点，不会被误当成选项', () => {
    const q = first(`1. 已知集合 A. 与集合 D. 的关系，判断下列说法
A. 正确
B. 错误
答案：A`)

    expect(q.options).toEqual(['正确', '错误'])
    expect(q.title).toContain('已知集合')
  })

  it('答案直接写在题干末尾的括号里', () => {
    const q = first(`1.Java 的入口方法是（A）
A. main
B. start`)

    expect(q.answer).toBe(0)
    expect(q.title).not.toContain('（A）')
  })

  it('题号分隔符可以是点、顿号、括号', () => {
    const styles = ['1.题干', '1、题干', '1）题干', '1) 题干', '(1) 题干', '（1）题干']

    styles.forEach((line) => {
      const result = parse(`${line}\nA. 甲\nB. 乙\n答案：A`)
      expect(result.success, `无法识别题号写法：${line}`).toBe(true)
      expect(result.questions[0].answer).toBe(0)
    })
  })
})

describe('多选题', () => {
  it('多字母答案自动判为多选', () => {
    const q = first(`1. 以下哪些是面向对象的特征？（多选）
A. 封装
B. 继承
C. 编译
D. 多态
答案：ABD`)

    expect(q.type).toBe('multi')
    expect(q.answer).toEqual([0, 1, 3])
  })

  it('题干里的题型提示会被清理掉，不留在标题里', () => {
    expect(first('1. 下列哪些正确？（多选）\nA. 甲\nB. 乙\n答案：AB').title).not.toContain('多选')
  })
})

describe('判断题', () => {
  it('识别「正确 / 错误」', () => {
    expect(first('1. Java 是面向对象语言。（判断）\n答案：正确').answer).toBe(true)
    expect(first('1. Java 不支持继承。（判断）\n答案：错误').answer).toBe(false)
  })

  it('识别对错与勾叉等写法', () => {
    const truthy = ['对', '√', '✓', 'true', 'T']
    const falsy = ['错', '×', '✗', 'false', 'F']

    truthy.forEach((word) => {
      expect(first(`1. 命题（判断）\n答案：${word}`).answer, word).toBe(true)
    })
    falsy.forEach((word) => {
      expect(first(`1. 命题（判断）\n答案：${word}`).answer, word).toBe(false)
    })
  })

  it('没有题型标注但答案是「正确」时也判为判断题', () => {
    expect(first('1. Java 是编译型语言。\n答案：正确').type).toBe('judge')
  })
})

describe('填空题与简答题', () => {
  it('题干含下划线判为填空', () => {
    const q = first('1. Java 中定义常量的关键字是____。\n答案：final')

    expect(q.type).toBe('blank')
    expect(q.answer).toBe('final')
    expect(q.options).toEqual([])
  })

  it('无选项无下划线无答案的判为简答', () => {
    const q = first('1. 简述抽象类与接口的区别。')

    expect(q.type).toBe('short')
    expect(q.options).toEqual([])
  })

  it('题干里标注「简答」时按简答处理', () => {
    expect(first('1.（简答）请说明 MVC 模式。\n答案：模型视图控制器').type).toBe('short')
  })
})

describe('整卷解析', () => {
  const paper = `一、单选题
1. Java 的基本类型有几种？
A. 6
B. 8
C. 10
答案：B

2. 以下哪些属于集合框架？（多选）
A. List  B. Map  C. Thread
答案：AB

二、判断题
3. String 是可变的。（判断）
答案：错误

三、填空题
4. JVM 的全称是____。
答案：Java Virtual Machine

四、简答题
5. 简述垃圾回收机制。`

  it('跳过题型大标题行，只识别真正的题目', () => {
    const result = parse(paper)

    expect(result.success).toBe(true)
    expect(result.questions).toHaveLength(5)
    expect(result.message).toContain('5')
  })

  it('各题题型识别正确', () => {
    expect(parse(paper).questions.map((q) => q.type)).toEqual([
      'single',
      'multi',
      'judge',
      'blank',
      'short'
    ])
  })

  it('每道题都有唯一 id 与默认分值', () => {
    const questions = parse(paper).questions
    const ids = questions.map((q) => q.id)

    expect(new Set(ids).size).toBe(questions.length)
    questions.forEach((q) => expect(q.score).toBeGreaterThan(0))
  })
})

describe('异常输入', () => {
  it('空内容给出可读提示而不是抛错', () => {
    const blanks = [null, undefined, '', '   ', '\n\n']

    blanks.forEach((input) => {
      const result = parse(input)
      expect(result.success).toBe(false)
      expect(result.questions).toEqual([])
      expect(result.message).toBeTruthy()
    })
  })

  it('完全无法识别的文本返回失败而不是造出空题', () => {
    const result = parse('这是一段普通的说明文字，没有任何题号。')

    expect(result.success).toBe(false)
    expect(result.questions).toEqual([])
  })

  it('题干跨多行时会被拼接，不会拆成两道题', () => {
    const result = parse(`1. 请问下面这段描述
是否正确？
A. 是
B. 否
答案：A`)

    expect(result.questions).toHaveLength(1)
    expect(result.questions[0].title).toContain('是否正确')
  })

  it('答案字母超出选项范围时置空，交由教师补全', () => {
    const q = first('1. 题干\nA. 甲\nB. 乙\n答案：D')

    expect(q.answer).toBeNull()
  })
})
