-- 原子性匹配并移除双方
-- KEYS[1]: match:pool (ZSet)
-- KEYS[2]: match:player:{playerId} (当前玩家信息 Hash)
-- KEYS[3]: match:player:{opponentId} (对手信息 Hash)
-- ARGV[1]: playerId
-- ARGV[2]: opponentId
--
-- 返回值:
--  1: 成功移除双方
--  0: 对手已不在池中（被其他人匹配走了）

local poolKey = KEYS[1]
local playerKey = KEYS[2]
local opponentKey = KEYS[3]
local playerId = ARGV[1]
local opponentId = ARGV[2]

-- 检查对手是否还在池中
local opponentScore = redis.call('ZSCORE', poolKey, opponentId)
if not opponentScore then
    return 0
end

-- 检查自己是否还在池中
local playerScore = redis.call('ZSCORE', poolKey, playerId)
if not playerScore then
    return 0
end

-- 原子性移除双方
redis.call('ZREM', poolKey, playerId)
redis.call('ZREM', poolKey, opponentId)
redis.call('DEL', playerKey)
redis.call('DEL', opponentKey)

return 1
