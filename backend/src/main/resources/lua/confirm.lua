-- 匹配确认 Lua 脚本
-- KEYS[1]: match:pair:{pairId} - 匹配对详情 Hash
-- KEYS[2]: match:player:pair:{playerId} - 当前玩家索引 String
-- KEYS[3]: match:player:pair:{opponentId} - 对手玩家索引 String
-- ARGV[1]: playerId (字符串)
--
-- 返回值:
-- -1: 匹配对不存在（已过期或被删除）
-- -2: 玩家不属于该匹配对
--  0: 玩家已确认，无需重复操作
--  1: 双方确认完成，已删除匹配对（需要额外处理创建场景）
--  2: 仅当前玩家确认，对方未确认
--
-- TTL 约定（与 MatchService 中的常量保持一致）：
--   匹配对 120s；玩家索引 150s。
--   玩家索引必须比匹配对存活更久：匹配对过期后、索引残留的那段时间里，
--   cleanExpiredMatchPairIndexes 才能发现“已超时”的匹配并通知双方。
--   首次确认时两者一起续期，维持该 TTL 差值不变。

local pairKey = KEYS[1]
local playerKey = KEYS[2]
local opponentKey = KEYS[3]
local playerId = ARGV[1]

-- 匹配对 TTL（秒），首次确认时续期，给对方留足确认时间
local PAIR_TTL_SECONDS = 120
-- 玩家索引 TTL（秒），恒比匹配对多 30s，形成超时清理的扫描窗口
local PLAYER_INDEX_TTL_SECONDS = 150
-- 临时 key TTL（秒），存双方确认后的玩家 ID，供 Java 读取
local TEMP_KEY_TTL_SECONDS = 30

-- 检查匹配对是否存在
local exists = redis.call('EXISTS', pairKey)
if exists == 0 then
    return -1
end

-- 获取匹配对信息
local player1Id = redis.call('HGET', pairKey, 'player1Id')
local player2Id = redis.call('HGET', pairKey, 'player2Id')

-- 检查玩家是否属于该匹配对
if playerId ~= player1Id and playerId ~= player2Id then
    return -2
end

-- 判断当前玩家是 player1 还是 player2
local confirmedField = ''
local opponentConfirmedField = ''
if playerId == player1Id then
    confirmedField = 'player1Confirmed'
    opponentConfirmedField = 'player2Confirmed'
else
    confirmedField = 'player2Confirmed'
    opponentConfirmedField = 'player1Confirmed'
end

-- 检查是否已确认
local alreadyConfirmed = redis.call('HGET', pairKey, confirmedField)
if alreadyConfirmed == '1' then
    return 0
end

-- 设置当前玩家确认状态
redis.call('HSET', pairKey, confirmedField, '1')

-- 续期：匹配对 + 双方玩家索引（索引始终比匹配对多 30s，保证清理任务有扫描窗口）
redis.call('EXPIRE', pairKey, PAIR_TTL_SECONDS)
redis.call('EXPIRE', playerKey, PLAYER_INDEX_TTL_SECONDS)
redis.call('EXPIRE', opponentKey, PLAYER_INDEX_TTL_SECONDS)

-- 检查对方是否已确认
local opponentConfirmed = redis.call('HGET', pairKey, opponentConfirmedField)

if opponentConfirmed == '1' then
    -- 双方都已确认，删除匹配对和双方索引
    redis.call('DEL', pairKey)
    redis.call('DEL', playerKey)
    redis.call('DEL', opponentKey)

    -- 返回 1 表示双方确认完成，同时存储玩家 ID 供 Java 读取
    -- 使用临时 key 存储玩家信息
    local tempKey = 'match:temp:confirmed:' .. pairKey
    redis.call('SET', tempKey, player1Id .. ':' .. player2Id)
    redis.call('EXPIRE', tempKey, TEMP_KEY_TTL_SECONDS)

    return 1
else
    -- 仅当前玩家确认，对方未确认
    return 2
end
