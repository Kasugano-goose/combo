import request from './request'

export function joinMatch() {
  return request.post('/match/join')
}

export function leaveMatch() {
  return request.post('/match/leave')
}

export function getMatchStatus() {
  return request.get('/match/status')
}

export function confirmMatch() {
  return request.post('/match/confirm')
}
