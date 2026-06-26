import request from './request'

export function joinMatch() {
  return request.post('/match/join')
}

export function leaveMatch() {
  return request.post('/match/leave')
}

export function getMatchStatus(playerId) {
  return request.get('/match/status', { params: { playerId } })
}

export function confirmMatch() {
  return request.post('/match/confirm')
}
