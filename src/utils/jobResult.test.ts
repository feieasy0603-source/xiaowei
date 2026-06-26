import { describe, expect, it } from 'vitest'
import { parseJobResult, resultDisplayText, TASK_TYPE_LABELS } from './jobResult'

describe('jobResult', () => {
  it('parses valid JSON', () => {
    const payload = parseJobResult(JSON.stringify({ revisedText: 'hello' }))
    expect(payload?.revisedText).toBe('hello')
    expect(resultDisplayText(payload)).toBe('hello')
  })

  it('returns null for invalid JSON', () => {
    expect(parseJobResult('{bad')).toBeNull()
  })

  it('includes task type labels', () => {
    expect(TASK_TYPE_LABELS.revise).toBe('改稿结果')
  })
})
