import { describe, expect, it } from 'vitest'
import { formatBytes } from './formatBytes'

describe('formatBytes', () => {
  it('keeps small files readable', () => {
    expect(formatBytes(512)).toBe('512 B')
  })

  it('uses decimal units for file sizes', () => {
    expect(formatBytes(1_500_000)).toBe('1.5 MB')
  })

  it('labels invalid sizes without throwing', () => {
    expect(formatBytes(-1)).toBe('—')
    expect(formatBytes(Number.NaN)).toBe('—')
  })
})
