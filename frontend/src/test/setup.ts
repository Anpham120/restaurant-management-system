import { afterEach } from 'vitest'
import { cleanup } from '@testing-library/react'

// Vitest runs without globals here, so Testing Library cannot unmount after each test by itself.
afterEach(cleanup)

// jsdom has no matchMedia, and Ant Design asks it about the screen size.
if (!window.matchMedia) {
  window.matchMedia = (query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: () => {},
    removeListener: () => {},
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false,
  })
}
