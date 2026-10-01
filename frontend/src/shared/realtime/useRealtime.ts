import { useEffect, useRef, useState } from 'react'
import { Client } from '@stomp/stompjs'
import type { RealtimeMessage } from '@/shared/api/types'

function brokerUrl(): string {
  const scheme = window.location.protocol === 'https:' ? 'wss' : 'ws'
  return `${scheme}://${window.location.host}/ws`
}

/**
 * Listens to STOMP topics and calls onMessage for every notice. Staff pass their token; guests pass none.
 * Returns whether the connection is up, so screens can show when updates are paused.
 */
export function useRealtime(
  topics: string[],
  onMessage: (message: RealtimeMessage) => void,
  token?: string | null,
): boolean {
  const handler = useRef(onMessage)
  const [connected, setConnected] = useState(false)
  const topicKey = topics.join('|')

  useEffect(() => {
    handler.current = onMessage
  }, [onMessage])

  useEffect(() => {
    if (!topicKey) return
    const client = new Client({
      brokerURL: brokerUrl(),
      connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
      reconnectDelay: 3000,
      onConnect: () => {
        setConnected(true)
        for (const topic of topicKey.split('|')) {
          client.subscribe(topic, (frame) => handler.current(JSON.parse(frame.body) as RealtimeMessage))
        }
      },
      onWebSocketClose: () => setConnected(false),
    })
    client.activate()
    return () => {
      void client.deactivate()
    }
  }, [topicKey, token])

  return connected
}
