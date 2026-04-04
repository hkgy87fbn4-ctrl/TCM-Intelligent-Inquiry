import { afterEach, describe, expect, it, vi } from 'vitest'

import {
  LITERATURE_TAB_COLLECTION_SESSION_KEY,
  setLiteratureTabCollectionId,
} from './literatureBeacon'

describe('setLiteratureTabCollectionId', () => {
  afterEach(() => {
    sessionStorage.clear()
  })

  it('stores trimmed id', () => {
    setLiteratureTabCollectionId('  abc  ')
    expect(sessionStorage.getItem(LITERATURE_TAB_COLLECTION_SESSION_KEY)).toBe(
      'abc'
    )
  })

  it('removes key for null', () => {
    sessionStorage.setItem(LITERATURE_TAB_COLLECTION_SESSION_KEY, 'x')
    setLiteratureTabCollectionId(null)
    expect(
      sessionStorage.getItem(LITERATURE_TAB_COLLECTION_SESSION_KEY)
    ).toBeNull()
  })

  it('ignores sessionStorage errors', () => {
    const spy = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('quota')
    })
    expect(() => setLiteratureTabCollectionId('id')).not.toThrow()
    spy.mockRestore()
  })
})
