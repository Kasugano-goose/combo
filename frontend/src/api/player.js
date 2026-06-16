import request from './request'

export function register(data) {
  return request.post('/players/register', data)
}

export function login(data) {
  return request.post('/players/login', data)
}

export function logout() {
  return request.post('/players/logout')
}

export function getMe() {
  return request.get('/players/me')
}

export function updateProfile(data) {
  return request.put('/players/me', data)
}

export function changePassword(data) {
  return request.put('/players/me/password', data)
}

export function selectRole(roleId) {
  return request.put('/players/me/role', { roleId })
}

export function deleteAccount() {
  return request.delete('/players/me')
}
