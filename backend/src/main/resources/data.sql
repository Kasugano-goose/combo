INSERT INTO player_rank (code, name, level, min_score, max_score)
VALUES
    ('BRONZE', '青铜', 1, 0, 999),
    ('SILVER', '白银', 2, 1000, 1999),
    ('GOLD', '黄金', 3, 2000, 2999),
    ('PLATINUM', '铂金', 4, 3000, 3999),
    ('DIAMOND', '钻石', 5, 4000, 4999),
    ('MASTER', '大师', 6, 5000, 2147483647);

INSERT INTO game_role (code, name, description)
VALUES
    ('WARRIOR', '战士', '近战输出角色，适合正面作战'),
    ('MAGE', '法师', '远程法术角色，适合爆发输出'),
    ('ASSASSIN', '刺客', '高机动角色，适合快速击杀'),
    ('TANK', '坦克', '防御型角色，适合承受伤害'),
    ('SUPPORT', '辅助', '团队支援角色，适合保护队友');

-- 玩家种子数据已清空
