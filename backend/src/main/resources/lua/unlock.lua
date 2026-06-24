-- 原子性释放锁（仅释放自己持有的锁）
-- KEYS[1]: 锁的 key
-- ARGV[1]: 锁的 value（持有者标识）
--
-- 返回值:
--  1: 成功释放
--  0: 锁不属于当前持有者或已不存在

local lockKey = KEYS[1]
local lockValue = ARGV[1]

local currentValue = redis.call('GET', lockKey)
if currentValue == lockValue then
    redis.call('DEL', lockKey)
    return 1
end

return 0
