const path = require('path')
const express = require('express')

const PORT = process.env.PORT || 9977
// Loopback by default: the mirror rebroadcasts whole analytics payloads, so it
// stays off the network until you opt in. A Simulator shares the Mac's loopback
// and needs nothing; a physical device needs HOST=0.0.0.0.
const HOST = process.env.HOST || '127.0.0.1'
const MAX_EVENTS = 2000

const app = express()
const clients = new Set()
const events = []

app.use(express.json({ limit: '2mb' }))
app.use(express.static(path.join(__dirname, 'public')))

app.post('/event', (req, res) => {
  const evt = { at: Date.now(), payload: req.body }
  events.push(evt)
  if (events.length > MAX_EVENTS) events.shift()
  for (const c of clients) c.write(`data: ${JSON.stringify(evt)}\n\n`)
  res.sendStatus(204)
})

app.get('/stream', (req, res) => {
  res.set({
    'Content-Type': 'text/event-stream',
    'Cache-Control': 'no-cache',
    Connection: 'keep-alive',
  })
  res.flushHeaders()
  res.write(`data: ${JSON.stringify({ replay: events })}\n\n`)
  clients.add(res)
  req.on('close', () => clients.delete(res))
})

app.delete('/events', (_req, res) => {
  events.length = 0
  res.sendStatus(204)
})

app.listen(PORT, HOST, () => {
  console.log(`mirror → http://localhost:${PORT}`)
  if (HOST === '127.0.0.1') console.log('loopback only — set HOST=0.0.0.0 to receive from a device on your LAN')
})
