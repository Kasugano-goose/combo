INSERT INTO player_rank (code, name, level, min_score, max_score, created_at, updated_at)
VALUES
    ('BRONZE', '青铜', 1, 0, 999, NOW(), NOW()),
    ('SILVER', '白银', 2, 1000, 1999, NOW(), NOW()),
    ('GOLD', '黄金', 3, 2000, 2999, NOW(), NOW()),
    ('PLATINUM', '铂金', 4, 3000, 3999, NOW(), NOW()),
    ('DIAMOND', '钻石', 5, 4000, 4999, NOW(), NOW()),
    ('MASTER', '大师', 6, 5000, 2147483647, NOW(), NOW());

INSERT INTO game_role (code, name, description, created_at, updated_at)
VALUES
    ('WARRIOR', '战士', '近战输出角色，适合正面作战', NOW(), NOW()),
    ('MAGE', '法师', '远程法术角色，适合爆发输出', NOW(), NOW()),
    ('ASSASSIN', '刺客', '高机动角色，适合快速击杀', NOW(), NOW()),
    ('TANK', '坦克', '防御型角色，适合承受伤害', NOW(), NOW()),
    ('SUPPORT', '辅助', '团队支援角色，适合保护队友', NOW(), NOW());

-- 玩家种子数据已清空
