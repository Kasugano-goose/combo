import request from './request'

export function sendFriendRequest(receiverId) {
  return request.post('/friends/requests', { receiverId })
}

export function acceptRequest(requestId) {
  return request.post(`/friends/requests/${requestId}/accept`)
}

export function rejectRequest(requestId) {
  return request.post(`/friends/requests/${requestId}/reject`)
}

export function getFriends() {
  return request.get('/friends')
}

export function getPendingRequests() {
  return request.get('/friends/requests')
}
