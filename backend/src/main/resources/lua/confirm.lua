-- 匹配确认 Lua 脚本
-- KEYS[1]: match:pair:{pairId} - 匹配对详情 Hash
-- KEYS[2]: match:player:pair:{playerId} - 玩家索引 String
-- ARGV[1]: playerId (字符串)
--
-- 返回值:
-- -1: 匹配对不存在（已过期或被删除）
-- -2: 玩家不属于该匹配对
--  0: 玩家已确认，无需重复操作
--  1: 双方确认完成，已删除匹配对（需要额外处理创建场景）
--  2: 仅当前玩家确认，对方未确认

local pairKey = KEYS[1]
local playerKey = KEYS[2]
local playerId = ARGV[1]

-- 匹配对 TTL（秒），首次确认时续期，给对方留足确认时间
local PAIR_TTL_SECONDS = 120
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

-- 续期 pairKey：首次确认后重新设置 TTL，防止对方确认时已过期
redis.call('EXPIRE', pairKey, PAIR_TTL_SECONDS)

-- 检查对方是否已确认
local opponentConfirmed = redis.call('HGET', pairKey, opponentConfirmedField)

if opponentConfirmed == '1' then
    -- 双方都已确认，删除匹配对和索引
    redis.call('DEL', pairKey)

    -- 删除两个玩家的索引
    redis.call('DEL', playerKey)
    local opponentKey = 'match:player:pair:' .. player2Id
    if playerId == player2Id then
        opponentKey = 'match:player:pair:' .. player1Id
    end
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
