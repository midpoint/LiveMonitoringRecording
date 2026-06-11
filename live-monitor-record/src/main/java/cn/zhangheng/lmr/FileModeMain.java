package cn.zhangheng.lmr;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.zhangheng.common.bean.Constant;
import cn.zhangheng.common.bean.Room;
import cn.zhangheng.common.bean.Setting;
import cn.zhangheng.common.bean.enums.RunMode;
import cn.zhangheng.common.httpServer.auth.AuthManager;
import cn.zhangheng.douyin.browser.DouYinBrowserFactory;
import cn.zhangheng.lmr.fileModeApi.LocalServerApi;
import com.zhangheng.util.ThrowableUtil;
import com.zhangheng.util.TimeUtil;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/09/25 星期四 01:15
 * @version: 1.0
 * @description: 通过直播监听文件的形式启动监听，可以同时监听多个
 */
@Slf4j
public class FileModeMain {
    private static final String basePath = "./";
    private static final String fileSuffix = ".room.json";
    @Getter
    private static ThreadPoolExecutor ThreadPool = null;
    @Getter
    private static final ConcurrentHashMap<Path, RoomFileModel> roomFileMap = new ConcurrentHashMap<>();
    @Getter
    private static final ConcurrentHashMap<Room.Platform, Integer> platformMap = new ConcurrentHashMap<>();
    private static LocalServerApi serverApi;
    private static final AtomicInteger runCount = new AtomicInteger(0);
    /** 已提交但尚未开始执行的文件，防止重复提交 */
    private static final Set<Path> pendingFiles = ConcurrentHashMap.newKeySet();

    /** 目录扫描间隔（秒） */
    private static final int SCAN_INTERVAL_SEC = 10;

    public static void main(String[] args) throws Exception {
        ScheduledExecutorService watcher = null;
        try {
            String path;
            if (args.length > 0) {
                path = args[0];
            } else {
                path = basePath;
            }
            Setting setting = new Setting();
            // 激活验证已移除

            // 初始化网页认证管理器
            AuthManager.init(setting);

            serverApi = new LocalServerApi(Constant.monitorServerPort);
            serverApi.start();

            // 初始扫描
            List<Path> paths = retrieveFile(path, fileSuffix);
            if (paths.isEmpty()) {
                log.info("{} 路径下暂无监听文件[{}]，等待新增...", path, fileSuffix);
            }

            // 使用最大线程数的线程池，支持后续新增
            int maxThreads = setting.getMaxMonitorThreads();
            ThreadPool = (ThreadPoolExecutor) Executors.newFixedThreadPool(maxThreads);
            log.info("启动监听线程池：最大{}个线程", maxThreads);

            // 启动初始文件的监听（先预加载使界面可见，再提交执行）
            for (int i = 0; i < paths.size(); i++) {
                Path file = paths.get(i);
                parseRoomFile(file);
                pendingFiles.add(file);
                ThreadPool.execute(() -> startMonitor(file));
                try {
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException ignored) {
                }
            }

            // 定时扫描目录，发现新增文件自动启动监听
            final String scanPath = path;
            watcher = Executors.newSingleThreadScheduledExecutor();
            watcher.scheduleWithFixedDelay(() -> {
                Thread.currentThread().setName("file-watcher");
                try {
                    List<Path> currentFiles = retrieveFile(scanPath, fileSuffix);
                    for (Path f : currentFiles) {
                        // 已在运行或已提交排队中的跳过
                        if (roomFileMap.containsKey(f) || pendingFiles.contains(f)) {
                            continue;
                        }
                        // 先解析预加载，使界面可见
                        if (parseRoomFile(f) != null) {
                            log.info("发现新增监听文件: {}", f);
                            pendingFiles.add(f);
                            try {
                                ThreadPool.execute(() -> startMonitor(f));
                            } catch (RejectedExecutionException e) {
                                pendingFiles.remove(f);
                                log.warn("线程池已满，无法启动新监听: {}", f);
                            }
                        }
                    }
                    // 清理已删除文件的记录
                    roomFileMap.keySet().removeIf(k -> !currentFiles.contains(k));
                    pendingFiles.removeIf(k -> !currentFiles.contains(k));
                } catch (Exception e) {
                    log.error("扫描监听文件异常: {}", e.getMessage());
                }
            }, SCAN_INTERVAL_SEC, SCAN_INTERVAL_SEC, TimeUnit.SECONDS);

        } catch (Exception e) {
            log.error(e.getMessage(), e);
        } finally {
            if (ThreadPool != null) {
                ThreadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
            }
            if (watcher != null) {
                watcher.shutdownNow();
            }
            if (serverApi != null) {
                serverApi.stop();
            }
        }

    }


    private static List<Path> retrieveFile(String basePath, String fileSuffix) {
        try (Stream<Path> list = Files.list(Paths.get(basePath))) {
            return list.filter(f -> f.getFileName().toString().endsWith(fileSuffix)).collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException("遍历文件出现异常", e);
        }
    }

    /**
     * 解析监听文件并预创建 RoomFileModel（不启动监听），立即加入 roomFileMap 使界面可见。
     * @return RoomFileModel 或 null（解析失败）
     */
    private static RoomFileModel parseRoomFile(Path file) {
        try {
            String s = String.join("", Files.readAllLines(file));
            JSONObject json = JSONUtil.parseObj(s);
            String id = json.getStr("id");
            Room.Platform platform = json.get("platform", Room.Platform.class);
            if (id == null || platform == null) {
                log.warn("{} 文件格式无效: id或platform缺失", file);
                return null;
            }
            String key = platform.name() + "-" + id;
            RoomFileModel model = new RoomFileModel();
            model.setId(key);
            model.setFilePath(file);
            roomFileMap.put(file, model);
            log.info("预加载监听文件: {} -> {}", file, key);
            return model;
        } catch (Exception e) {
            log.warn("解析监听文件失败 {}: {}", file, e.getMessage());
            return null;
        }
    }

    public static void startMonitor(Path file) {
        String key;
        RoomFileModel model = null;
        try {
            //解析文件
            String s = String.join("", Files.readAllLines(file));
            JSONObject json = JSONUtil.parseObj(s);
            Boolean isRecord = json.getBool("isRecord", false);
            String id = json.getStr("id");
            Room.Platform platform = json.get("platform", Room.Platform.class);
            Setting setting = json.get("setting", Setting.class);
            if (setting != null) {
                setting.setRunMode(RunMode.FILE);
            } else {
                log.warn("{}监听文件没有setting", file);
            }
            //直播间标识
            key = platform.name() + "-" + id;
            Thread.currentThread().setName(key);
            // 复用 parseRoomFile 预创建的 model，或新建
            model = roomFileMap.get(file);
            if (model == null) {
                model = new RoomFileModel();
                model.setId(key);
                model.setFilePath(file);
                roomFileMap.put(file, model);
            }
            pendingFiles.remove(file);
            Main main = new Main();
            model.setMain(main);
            runCount.incrementAndGet();
            platformMap.compute(platform, (k, v) -> v == null ? 1 : v + 1);
            log.info("{} 监听开始运行!", file);
            model.setStartTime();
            main.start(setting, id, platform, isRecord);
            log.info("{} 监听结束运行!", file);
        } catch (Throwable e) {
            log.error(file + " 监听发生异常:" + e.getMessage(), e);
        } finally {
            endMonitor(model);
        }
    }

    private static void endMonitor(RoomFileModel model) {
        if (model == null) return;
        runCount.decrementAndGet();
        model.setEndTime();
        Room.Platform platform = model.getMain().getRoom().getPlatform();
        platformMap.compute(platform, (k, v) -> v == null ? 0 : v - 1);
        if (platformMap.get(Room.Platform.DouYin) == null || platformMap.get(Room.Platform.DouYin) < 1) {
            DouYinBrowserFactory.closeBrowser();
        }
        log.info("{}个监听运行情况：{}", runCount.get(), platformMap);
        // 不再主动退出 —— watcher 线程持续扫描新文件
//        executeFileMap.remove(model.getId());
    }

    public static void restartMain(String key) throws RuntimeException {
        try {
            ThreadPool.execute(() -> {
                RoomFileModel model = getModelById(key);
                if (model == null) {
                    log.warn("{}标识的不存在，无法重新启动监听", key);
                    return;
                }
                startMonitor(model.getFilePath());
            });
        } catch (RejectedExecutionException e) {
            // 处理任务被拒绝的情况（如线程池关闭、队列满等）
            String s = "监听任务提交失败，线程池可能已关闭或任务队列已满：" + ThrowableUtil.getAllCauseMessage(e);
            log.warn(s);
            throw new RuntimeException(s);
        } catch (Exception e) {
            String s = "提交监听任务发生异常" + ThrowableUtil.getAllCauseMessage(e);
            log.error("提交任务发生异常: {}", s);
            throw new RuntimeException(s);
        }
    }

    public static RoomFileModel getModelById(String id) {
        return roomFileMap.values().stream().filter(m -> m.getId().equals(id)).findFirst().orElse(null);
    }

    public static ConcurrentHashMap<Path, RoomFileModel> getRoomFileMap() {
        return roomFileMap;
    }

    public static Map<String, Object> getCounter() {
        Map<String, Object> platformData = new HashMap<>();
        for (RoomFileModel model : roomFileMap.values()) {
            Map<String, Object> counter = new HashMap<>();
            if (model.getMain() != null && model.getMain().getMonitorMain() != null) {
                Room room = model.getMain().getMonitorMain().getRoom();
                counter.put("name", room.getNickname());
                counter.put("url", room.getRoomUrl());
                counter.put("platform", room.getPlatform().getName());
                counter.put("living", room.isLiving());
                counter.put("intervalSec", room.getSetting().getDelayIntervalSec());
                counter.put("count", model.getMain().getMonitorMain().getRoomMonitor().getCount());
                counter.put("updateTime", TimeUtil.toTime(room.getUpdateTime()));
            } else {
                // pending 状态：尚未开始执行
                counter.put("name", model.getId());
                counter.put("url", "");
                counter.put("platform", "");
                counter.put("living", false);
                counter.put("intervalSec", 0);
                counter.put("count", 0);
                counter.put("updateTime", "等待中...");
            }
            platformData.put(model.getId(), counter);
        }
        return platformData;
    }
}
