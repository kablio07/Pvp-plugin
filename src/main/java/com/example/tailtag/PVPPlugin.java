// plugin.yml
/*
name: PVPPlugin
version: 1.0
main: com.example.pvpplugin.PVPPlugin
api-version: 1.19
author: YourName
description: 마인크래프트 PVP 플러그인

commands:
  game:
    description: 게임 관리 명령어
    usage: /game <start|reset>
*/

package com.example.pvpplugin;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Score;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class PVPPlugin extends JavaPlugin implements Listener, CommandExecutor {
    
    private boolean gameStarted = false;
    private boolean killTimeStarted = false;
    private List<Player> alivePlayers = new ArrayList<>();
    private BukkitTask invincibilityTask;
    private BukkitTask borderShrinkTask;
    private BukkitTask scoreboardTask;
    private World gameWorld;
    private WorldBorder worldBorder;
    private int currentBorderSize = 2000; // 초기 크기 2000 (-1000 ~ 1000)
    private Scoreboard gameScoreboard;
    private Objective gameObjective;
    private int gameTimeMinutes = 0;
    
    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        this.getCommand("game").setExecutor(this);
        getLogger().info("PVP 플러그인이 활성화되었습니다!");
    }
    
    @Override
    public void onDisable() {
        resetGame();
        getLogger().info("PVP 플러그인이 비활성화되었습니다!");
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("game")) {
            return false;
        }
        
        if (args.length == 0) {
            sender.sendMessage("§c사용법: /game start 또는 /game reset");
            return true;
        }
        
        switch (args[0].toLowerCase()) {
            case "start":
                if (gameStarted) {
                    sender.sendMessage("§c게임이 이미 시작되었습니다!");
                    return true;
                }
                startGame();
                break;
                
            case "reset":
                resetGame();
                sender.sendMessage("§a게임이 초기화되었습니다!");
                break;
                
            default:
                sender.sendMessage("§c사용법: /game start 또는 /game reset");
                break;
        }
        return true;
    }
    
    private void startGame() {
        gameStarted = true;
        killTimeStarted = false;
        currentBorderSize = 2000;
        gameTimeMinutes = 0;
        gameStartTime = System.currentTimeMillis();
        alivePlayers.clear();
        
        // 게임 월드 설정 (첫 번째 월드 사용)
        gameWorld = Bukkit.getWorlds().get(0);
        worldBorder = gameWorld.getWorldBorder();
        
        // 월드보더 설정
        worldBorder.setCenter(0, 0);
        worldBorder.setSize(currentBorderSize);
        worldBorder.setWarningDistance(50);
        worldBorder.setWarningTime(10);
        
        // 스코어보드 설정
        setupScoreboard();
        
        // 모든 플레이어 설정
        for (Player player : Bukkit.getOnlinePlayers()) {
            setupPlayer(player);
            alivePlayers.add(player);
        }
        
        Bukkit.broadcastMessage("§a🛡️ PVP 게임이 시작되었습니다!");
        Bukkit.broadcastMessage("§e30분 후 킬타임이 시작됩니다!");
        
        // 스코어보드 업데이트 시작 (1초마다)
        startScoreboardUpdate();
        
        // 30분 후 킬타임 시작
        invincibilityTask = new BukkitRunnable() {
            @Override
            public void run() {
                startKillTime();
            }
        }.runTaskLater(this, 36000L); // 30분 = 36000 틱
    }
    
    private void setupPlayer(Player player) {
        // 무작위 스폰 위치 (-1000 ~ 1000)
        Random random = new Random();
        int x = random.nextInt(2000) - 1000;
        int z = random.nextInt(2000) - 1000;
        int y = gameWorld.getHighestBlockYAt(x, z) + 1;
        
        Location spawnLocation = new Location(gameWorld, x, y, z);
        player.teleport(spawnLocation);
        
        // 게임모드 및 상태 설정
        player.setGameMode(GameMode.SURVIVAL);
        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        player.setLevel(50);
        player.setExp(0.0f);
        
        // 무적 효과 부여 (30분)
        player.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 36000, 255, false, false));
        
        // 인벤토리 초기화 및 아이템 지급
        player.getInventory().clear();
        player.getInventory().addItem(new ItemStack(Material.BOOKSHELF, 64));
        player.getInventory().addItem(new ItemStack(Material.ENCHANTING_TABLE, 1));
        
        // 스코어보드 설정
        if (gameScoreboard != null) {
            player.setScoreboard(gameScoreboard);
        }
        
        player.sendMessage("§a게임에 참가했습니다! 30분의 무적 시간이 부여됩니다.");
    }
    
    private void startKillTime() {
        killTimeStarted = true;
        
        // 모든 플레이어의 무적 해제
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getGameMode() == GameMode.SURVIVAL) {
                player.removePotionEffect(PotionEffectType.DAMAGE_RESISTANCE);
            }
        }
        
        Bukkit.broadcastMessage("§c⚠️ 킬타임 시작!");
        Bukkit.broadcastMessage("§e월드보더가 축소되기 시작합니다!");
        
        // 월드보더 축소 시작 (5분마다 200씩)
        startBorderShrinking();
    }
    
    private void startBorderShrinking() {
        borderShrinkTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!gameStarted || !killTimeStarted) {
                    cancel();
                    return;
                }
                
                currentBorderSize -= 400; // x, z 각각 200씩 축소 (총 400)
                
                if (currentBorderSize <= 0) {
                    currentBorderSize = 1; // 최소 크기 1칸
                    cancel();
                    return;
                }
                
                worldBorder.setSize(currentBorderSize, 10); // 10초에 걸쳐 축소
                Bukkit.broadcastMessage("§c월드보더가 축소되었습니다! 현재 크기: " + currentBorderSize + "x" + currentBorderSize);
            }
        }.runTaskTimer(this, 6000L, 6000L); // 5분마다 실행 (6000틱)
    }
    
    private void resetGame() {
        gameStarted = false;
        killTimeStarted = false;
        
        // 실행 중인 작업 취소
        if (invincibilityTask != null) {
            invincibilityTask.cancel();
        }
        if (borderShrinkTask != null) {
            borderShrinkTask.cancel();
        }
        if (scoreboardTask != null) {
            scoreboardTask.cancel();
        }
        
        // 월드보더 초기화
        if (worldBorder != null) {
            worldBorder.setSize(60000000); // 기본 크기로 복원
        }
        
        // 모든 플레이어 초기화
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setGameMode(GameMode.SURVIVAL);
            player.removePotionEffect(PotionEffectType.DAMAGE_RESISTANCE);
            player.setHealth(20.0);
            player.setFoodLevel(20);
            player.getInventory().clear();
            player.setLevel(0);
            player.setExp(0.0f);
            // 기본 스코어보드로 복원
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
        
        alivePlayers.clear();
        gameTimeMinutes = 0;
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (gameStarted && !killTimeStarted) {
            // 게임 진행 중이지만 킬타임 전이면 참가 가능
            setupPlayer(event.getPlayer());
            alivePlayers.add(event.getPlayer());
        } else if (gameStarted) {
            // 게임 진행 중이면 스코어보드만 설정
            if (gameScoreboard != null) {
                event.getPlayer().setScoreboard(gameScoreboard);
            }
        }
    }
    
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!gameStarted) return;
        
        Player player = event.getEntity();
        alivePlayers.remove(player);
        
        // 관전자 모드로 변경
        new BukkitRunnable() {
            @Override
            public void run() {
                player.setGameMode(GameMode.SPECTATOR);
                player.sendMessage("§c당신은 죽었습니다. 관전자 모드로 전환됩니다.");
            }
        }.runTaskLater(this, 1L);
        
        // 승리 조건 확인
        checkWinCondition();
    }
    
    private void checkWinCondition() {
        List<Player> survivors = new ArrayList<>();
        for (Player player : alivePlayers) {
            if (player.isOnline() && player.getGameMode() == GameMode.SURVIVAL) {
                survivors.add(player);
            }
        }
        
        if (survivors.size() == 1) {
            // 승리!
            Player winner = survivors.get(0);
            Bukkit.broadcastMessage("§6🏆 게임 종료!");
            Bukkit.broadcastMessage("§a승리자: " + winner.getName());
            
            resetGame();
        } else if (survivors.size() == 0) {
            // 무승부
            Bukkit.broadcastMessage("§6게임 종료! 생존자가 없습니다.");
            resetGame();
        }
    }
    
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!gameStarted) return;
        
        Block block = event.getBlock();
        Material type = block.getType();
        Location location = block.getLocation();
        
        // 광석 드랍 변경
        switch (type) {
            case IRON_ORE:
            case DEEPSLATE_IRON_ORE:
                event.setDropItems(false);
                location.getWorld().dropItemNaturally(location, new ItemStack(Material.IRON_INGOT, 1));
                break;
                
            case GOLD_ORE:
            case DEEPSLATE_GOLD_ORE:
            case NETHER_GOLD_ORE:
                event.setDropItems(false);
                location.getWorld().dropItemNaturally(location, new ItemStack(Material.GOLD_INGOT, 1));
                break;
                
            case EMERALD_ORE:
            case DEEPSLATE_EMERALD_ORE:
                event.setDropItems(false);
                location.getWorld().dropItemNaturally(location, new ItemStack(Material.DIAMOND, 1));
                break;
        }
        
        // 나뭇잎에서 사과 드랍 (가위 사용시)
        if (isLeafBlock(type) && event.getPlayer().getInventory().getItemInMainHand().getType() == Material.SHEARS) {
            Random random = new Random();
            if (random.nextDouble() < 0.1) { // 10% 확률
                location.getWorld().dropItemNaturally(location, new ItemStack(Material.APPLE, 1));
            }
        }
    }
    
    @EventHandler
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        if (!gameStarted) return;
        
        Player player = event.getPlayer();
        
        // 다음 틱에 아이템 속성 수정 (아이템 변경이 완료된 후)
        new BukkitRunnable() {
            @Override
            public void run() {
                ItemStack item = player.getInventory().getItemInMainHand();
                if (item != null && item.getType() != Material.AIR) {
                    removeAttackSpeed(item);
                }
            }
        }.runTaskLater(this, 1L);
    }
    
    private void removeAttackSpeed(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return;
        
        // 도구나 무기인지 확인
        Material type = item.getType();
        if (isWeaponOrTool(type)) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                // 공격 속도 속성 제거 후 최대값으로 설정
                meta.removeAttributeModifier(Attribute.GENERIC_ATTACK_SPEED);
                meta.addAttributeModifier(Attribute.GENERIC_ATTACK_SPEED, 
                    new AttributeModifier("generic.attack_speed", 1024, 
                    AttributeModifier.Operation.ADD_NUMBER));
                item.setItemMeta(meta);
            }
        }
    }
    
    private boolean isWeaponOrTool(Material material) {
        return material.name().endsWith("_SWORD") ||
               material.name().endsWith("_AXE") ||
               material.name().endsWith("_PICKAXE") ||
               material.name().endsWith("_SHOVEL") ||
               material.name().endsWith("_HOE") ||
               material == Material.TRIDENT;
    }
        return material == Material.OAK_LEAVES ||
               material == Material.BIRCH_LEAVES ||
               material == Material.SPRUCE_LEAVES ||
               material == Material.JUNGLE_LEAVES ||
               material == Material.ACACIA_LEAVES ||
               material == Material.DARK_OAK_LEAVES ||
               material == Material.MANGROVE_LEAVES ||
               material == Material.CHERRY_LEAVES ||
               material == Material.AZALEA_LEAVES ||
               material == Material.FLOWERING_AZALEA_LEAVES;
    }
    
    private void setupScoreboard() {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        gameScoreboard = manager.getNewScoreboard();
        gameObjective = gameScoreboard.registerNewObjective("pvpgame", "dummy", "§6§l🛡️ PVP 배틀로얄");
        gameObjective.setDisplaySlot(DisplaySlot.SIDEBAR);
    }
    
    private void startScoreboardUpdate() {
        scoreboardTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!gameStarted) {
                    cancel();
                    return;
                }
                
                updateScoreboard();
                
                // 1분마다 시간 증가
                if (this.getTaskId() % 1200 == 0) { // 1200틱 = 1분
                    gameTimeMinutes++;
                }
            }
        }.runTaskTimer(this, 0L, 20L); // 1초마다 실행
    }
    
    private void updateScoreboard() {
        if (gameObjective == null) return;
        
        // 기존 점수 제거
        gameScoreboard.getEntries().forEach(entry -> gameScoreboard.resetScores(entry));
        
        // 현재 시간 계산
        int currentMinutes = gameTimeMinutes + (int)(System.currentTimeMillis() - gameStartTime) / 60000;
        int remainingMinutes = Math.max(0, 30 - currentMinutes);
        
        // 생존자 수 계산
        int aliveCount = 0;
        for (Player player : alivePlayers) {
            if (player.isOnline() && player.getGameMode() == GameMode.SURVIVAL) {
                aliveCount++;
            }
        }
        
        // 스코어보드 내용 설정
        int line = 15;
        
        setScore("§7§m                    ", line--);
        
        if (killTimeStarted) {
            setScore("§c⚔️ §l킬타임 진행중", line--);
        } else {
            setScore("§a🛡️ §l준비시간", line--);
            setScore("§e무적 해제까지: §f" + remainingMinutes + "분", line--);
        }
        
        setScore("", line--);
        setScore("§f👥 생존자: §a" + aliveCount + "명", line--);
        setScore("§f🕐 경과시간: §e" + currentMinutes + "분", line--);
        
        if (killTimeStarted) {
            setScore("§f📏 자기장: §c" + currentBorderSize + "x" + currentBorderSize, line--);
        } else {
            setScore("§f📏 자기장: §a2000x2000", line--);
        }
        
        setScore("§7§m                    ", line--);
    }
    
    private long gameStartTime = 0;
    
    private void setScore(String text, int score) {
        if (gameObjective != null) {
            Score s = gameObjective.getScore(text);
            s.setScore(score);
        }
    }
}
