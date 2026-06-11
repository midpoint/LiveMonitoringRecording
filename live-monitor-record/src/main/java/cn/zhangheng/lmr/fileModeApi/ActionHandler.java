package cn.zhangheng.lmr.fileModeApi;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.zhangheng.common.bean.Constant;
import cn.zhangheng.common.httpServer.handle.JSONHandler;
import cn.zhangheng.common.service.MonitorMain;
import cn.zhangheng.common.bean.Room;
import cn.zhangheng.common.bean.Setting;
import cn.zhangheng.common.bean.enums.MonitorStatus;
import cn.zhangheng.common.record.Recorder;
import cn.zhangheng.douyin.bean.DouYinVideo;
import cn.zhangheng.douyin.browser.DouYinBrowserFactory;
import cn.zhangheng.douyin.browser.DouYinVideoParse;
import cn.zhangheng.lmr.FileModeMain;
import cn.zhangheng.lmr.Main;
import cn.zhangheng.lmr.RoomFileModel;
import com.sun.net.httpserver.HttpExchange;
import com.zhangheng.bean.Message;
import com.zhangheng.util.ThrowableUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;


/**
 * @author: ZhangHeng
 * @email: zhangheng_0805@163.com
 * @date: 2025/09/27 星期六 21:22
 * @version: 1.0
 * @description:
 */
public class ActionHandler extends JSONHandler {

    protected ActionHandler(String prefix) {
        super(prefix);
    }


    @Override
    public void request(HttpExchange httpExchange) throws IOException {
        String indexPath = getIndexPath(httpExchange, prefix);
        Message<Object> msg = new Message<>();
        try {
            if (indexPath.startsWith("monitor")) {
                Map<String, String> query = parseQuery(httpExchange);
                if ("127.0.0.1".equals(getClientIP(httpExchange))) query.put("actionKey", Constant.deviceUniqueId);
                if (checkActionKey(query, msg) && checkRoomKey(query, msg)) {
                    actionMonitor(msg, query);
                } else {
                    msg.setCode(1);
                }
            } else if (indexPath.startsWith("record")) {
                Map<String, String> query = parseQuery(httpExchange);
                if ("127.0.0.1".equals(getClientIP(httpExchange))) query.put("actionKey", Constant.deviceUniqueId);
                if (checkActionKey(query, msg) && checkRoomKey(query, msg)) {
                    actionRecord(msg, query);
                } else {
                    msg.setCode(1);
                }
            } else if (indexPath.startsWith("setting")) {
                Map<String, String> query = parseQuery(httpExchange);
                if ("127.0.0.1".equals(getClientIP(httpExchange))) query.put("actionKey", Constant.deviceUniqueId);
                if (checkActionKey(query, msg) && checkRoomKey(query, msg)) {
                    actionSetting(msg, query);
                } else {
                    msg.setCode(1);
                }
            } else if (indexPath.startsWith("refresh")) {
                Map<String, String> query = parseQuery(httpExchange);
                if (checkRoomKey(query, msg)) {
                    actionRefresh(msg, query);
                } else {
                    msg.setCode(1);
                }
            } else if (indexPath.startsWith("getThread")) {
                getThread(msg);
            } else if (indexPath.startsWith("getCount")) {
                Map<String, Object> douYinCounter = DouYinBrowserFactory.getBrowser().getCount();
                Map<String, Object> allCounter = FileModeMain.getCounter();
                Map<String, Object> data = new HashMap<>();
                data.put("DouYinCounter", douYinCounter);
                data.put("AllCounter", allCounter);
                msg.setData(data);
            } else if (indexPath.startsWith("clear")) {
                Map<String, String> query = parseQuery(httpExchange);
                if (checkActionKey(query, msg)) {
                    DouYinBrowserFactory.getBrowser().threadLocalClear();
                    msg.setMessage("清理成功！");
                } else {
                    msg.setCode(1);
                }
            } else if (indexPath.startsWith("close")) {
                Map<String, String> query = parseQuery(httpExchange);
                if (checkActionKey(query, msg)) {
                    boolean b = DouYinBrowserFactory.closeBrowser();
                    msg.setCode(b ? 0 : 1);
                    msg.setMessage("浏览器重启" + (b ? "成功" : "失败"));
                } else {
                    msg.setCode(1);
                }
            } else if (indexPath.startsWith("videoParsing")) {
                Map<String, String> query = parseQuery(httpExchange);
                videoParsing(msg, query);
            } else if (indexPath.startsWith("startAll")) {
                Map<String, String> query = parseQuery(httpExchange);
                if ("127.0.0.1".equals(getClientIP(httpExchange))) query.put("actionKey", Constant.deviceUniqueId);
                if (checkActionKey(query, msg)) {
                    actionStartAll(msg);
                } else {
                    msg.setCode(1);
                }
            } else if (indexPath.startsWith("stopAll")) {
                Map<String, String> query = parseQuery(httpExchange);
                if ("127.0.0.1".equals(getClientIP(httpExchange))) query.put("actionKey", Constant.deviceUniqueId);
                if (checkActionKey(query, msg)) {
                    actionStopAll(msg);
                } else {
                    msg.setCode(1);
                }
            } else if (indexPath.startsWith("addRoom")) {
                Map<String, String> query = parseQuery(httpExchange);
                if ("127.0.0.1".equals(getClientIP(httpExchange))) query.put("actionKey", Constant.deviceUniqueId);
                if (checkActionKey(query, msg)) {
                    actionAddRoom(msg, query);
                } else {
                    msg.setCode(1);
                }
            } else {
                msg.setCode(1);
                msg.setMessage("访问的接口路径不存在！" + prefix + indexPath);
            }
        } catch (Throwable throwable) {
            msg.setCode(1);
            msg.setTitle("接口异常！");
            msg.setMessage(ThrowableUtil.getAllCauseMessage(throwable));
            throwable.printStackTrace();
        }
//        System.out.println(msg);
        responseJson(httpExchange, msg);
    }

    private synchronized void actionMonitor(Message msg, Map<String, String> query) {
        String key = query.get("key");
        boolean flag = Boolean.parseBoolean(query.get("flag"));
        RoomFileModel model = FileModeMain.getModelById(key);
        if (model == null) {
            msg.setCode(1);
            msg.setMessage("标识" + key + "不存在！");
            return;
        }
        Main main = model.getMain();
        MonitorMain<Room, ?> monitorMain = main.getMonitorMain();
        if (flag == monitorMain.getIsRunning()) {
            msg.setCode(1);
            msg.setMessage(StrUtil.format("监听状态已{},请勿重复操作！", flag ? "开启" : "关闭"));
            return;
        }
        if (flag) {
            try {
                FileModeMain.restartMain(key);
            } catch (RuntimeException e) {
                msg.setCode(1);
                msg.setMessage(e.getMessage());
                return;
            }
            msg.setMessage("监听启动成功！");
        } else {
            monitorMain.setIsForceStop(true);
            monitorMain.stop();
            msg.setMessage("标识[" + key + "]的直播监听已关闭！");
        }

    }

    private synchronized void actionRecord(Message msg, Map<String, String> query) {
        String key = query.get("key");
        boolean flag = Boolean.parseBoolean(query.get("flag"));
        RoomFileModel model = FileModeMain.getModelById(key);
        if (model == null) {
            msg.setCode(1);
            msg.setMessage("标识" + key + "不存在！");
            return;
        }
        Main main = model.getMain();

        MonitorMain<Room, ?> monitorMain = main.getMonitorMain();
        Recorder recorder = monitorMain.getRecorder();
        boolean isRecord = recorder != null && recorder.isRunning();
        if (flag == isRecord) {
            msg.setCode(1);
            msg.setMessage(StrUtil.format("录制状态已{},请勿重复操作！", flag ? "开启" : "停止"));
            return;
        }
        boolean res;
        if (flag) {
            res = monitorMain.startRecord();
        } else {
            res = monitorMain.stopRecord();
        }
        msg.setMessage(StrUtil.format("{}录制{}！", flag ? "开启" : "停止", res ? "成功" : "失败"));

    }

    private synchronized void actionRefresh(Message msg, Map<String, String> query) {
        String key = query.get("key");
        RoomFileModel model = FileModeMain.getModelById(key);
        if (model == null) {
            msg.setCode(1);
            msg.setMessage("标识" + key + "不存在！");
            return;
        }
        Main main = model.getMain();
        MonitorMain<Room, ?> monitorMain = main.getMonitorMain();
        if (monitorMain.getStatus() != MonitorStatus.RUNNING) {
            msg.setCode(1);
            msg.setMessage("该直播间没有启动监听!");
        }
        try {
            monitorMain.getRoomMonitor().nowRefresh();
            msg.setMessage("刷新成功!");
        } catch (Exception e) {
            msg.setCode(1);
            msg.setMessage("刷新失败!");
        }
    }

    private synchronized void actionSetting(Message msg, Map<String, String> query) {
        String key = query.get("key");
        RoomFileModel model = FileModeMain.getModelById(key);
        if (model == null) {
            msg.setCode(1);
            msg.setMessage("标识" + key + "不存在！");
            return;
        }
        Main main = model.getMain();
        try {
            MonitorMain<Room, ?> monitorMain = main.getMonitorMain();
            if (query.containsKey("delayIntervalSec")) {
                int delayIntervalSec = Integer.parseInt(query.get("delayIntervalSec"));
                monitorMain.getRoom().getSetting().setDelayIntervalSec(delayIntervalSec);
            }
            if (query.containsKey("convertFlvToMp4")) {
                boolean convertFlvToMp4 = Boolean.parseBoolean(query.get("convertFlvToMp4"));
                monitorMain.getRoom().getSetting().setConvertFlvToMp4(convertFlvToMp4);
            }
            if (query.containsKey("openSubtitle")) {
                boolean openSubtitle = Boolean.parseBoolean(query.get("openSubtitle"));
                monitorMain.getRoom().getSetting().setOpenSubtitle(openSubtitle);
            }
            if (query.containsKey("isLoop")) {
                boolean isLoop = Boolean.parseBoolean(query.get("isLoop"));
                monitorMain.getRoom().getSetting().setLoop(isLoop);
            }
            if (query.containsKey("cookie")) {
                String cookie = query.get("cookie");
                if (StrUtil.isNotBlank(cookie)) {
                    monitorMain.getRoom().setCookie(cookie);
                }
            }
            msg.setMessage("设置成功!");
        } catch (Exception e) {
            msg.setCode(1);
            msg.setMessage("设置错误! " + ThrowableUtil.getAllCauseMessage(e));
        }
    }

    private void getThread(Message msg) {
        ThreadPoolExecutor threadPool = FileModeMain.getThreadPool();
        int corePoolSize = threadPool.getCorePoolSize();
        int activeCount = threadPool.getActiveCount();
        int remainingThreads = corePoolSize - activeCount;
        Map<String, Integer> res = new HashMap<>();
        res.put("corePoolSize", corePoolSize);
        res.put("activeCount", activeCount);
        res.put("remainingThreads", remainingThreads);
        msg.setData(res);
        msg.setMessage(StrUtil.format("核心线程数: {}， 正在工作的线程数: {}, 剩余可用线程数: {}", corePoolSize, activeCount, remainingThreads));
    }

    private void actionStartAll(Message msg) {
        int started = 0, alreadyRunning = 0;
        for (RoomFileModel model : FileModeMain.getRoomFileMap().values()) {
            if (model.getMain() != null && model.getMain().getMonitorMain() != null) {
                if (model.getMain().getMonitorMain().getIsRunning()) {
                    alreadyRunning++;
                } else {
                    try {
                        FileModeMain.restartMain(model.getId());
                        started++;
                    } catch (Exception e) {
                        System.err.println("重启监听失败[" + model.getId() + "]: " + e.getMessage());
                    }
                }
            }
        }
        msg.setMessage(StrUtil.format("已启动{}个监听, {}个已在运行", started, alreadyRunning));
    }

    private void actionStopAll(Message msg) {
        int stopped = 0, alreadyStopped = 0;
        for (RoomFileModel model : FileModeMain.getRoomFileMap().values()) {
            if (model.getMain() != null && model.getMain().getMonitorMain() != null) {
                if (model.getMain().getMonitorMain().getIsRunning()) {
                    model.getMain().getMonitorMain().setIsForceStop(true);
                    model.getMain().getMonitorMain().stop();
                    stopped++;
                } else {
                    alreadyStopped++;
                }
            }
        }
        msg.setMessage(StrUtil.format("已停止{}个监听, {}个已处于停止状态", stopped, alreadyStopped));
    }

    private synchronized void actionAddRoom(Message msg, Map<String, String> query) {
        String platformStr = query.get("platform");
        String roomId = query.get("roomId");
        boolean isRecord = Boolean.parseBoolean(query.getOrDefault("isRecord", "true"));

        if (StrUtil.isBlank(platformStr) || StrUtil.isBlank(roomId)) {
            msg.setCode(1);
            msg.setMessage("平台和直播间ID不能为空");
            return;
        }

        Room.Platform platform;
        try {
            platform = Room.Platform.valueOf(platformStr);
        } catch (IllegalArgumentException e) {
            msg.setCode(1);
            msg.setMessage("不支持的平台: " + platformStr + "，支持: DouYin, Bili, KuaiShou");
            return;
        }

        String key = platform.name() + "-" + roomId;
        // 检查是否已存在
        if (FileModeMain.getModelById(key) != null) {
            msg.setCode(1);
            msg.setMessage("直播间 [" + key + "] 已在监控列表中");
            return;
        }

        try {
            // 构造 .room.json 文件内容
            JSONObject roomJson = new JSONObject();
            roomJson.set("id", roomId);
            roomJson.set("platform", platform.name());
            roomJson.set("isRecord", isRecord);
            JSONObject settingJson = new JSONObject();
            settingJson.set("runMode", "FILE");
            roomJson.set("setting", settingJson);

            // 写入文件
            String fileName = roomId + ".room.json";
            Path dirPath = Paths.get(FileModeMain.getMonitorDirPath());
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
            }
            Path filePath = dirPath.resolve(fileName);
            Files.write(filePath, roomJson.toStringPretty().getBytes(StandardCharsets.UTF_8));

            // 立即预加载到界面
            RoomFileModel model = new RoomFileModel();
            model.setId(key);
            model.setFilePath(filePath);
            FileModeMain.getRoomFileMap().put(filePath, model);

            // 提交到线程池启动监听
            try {
                FileModeMain.getThreadPool().execute(() -> FileModeMain.startMonitor(filePath));
                msg.setMessage("直播间 [" + key + "] 已添加，正在启动监控...");
            } catch (RejectedExecutionException e) {
                msg.setCode(1);
                msg.setMessage("线程池已满，无法启动新监听。文件已保存，下次扫描时自动启动。");
            }
        } catch (Exception e) {
            msg.setCode(1);
            msg.setMessage("添加失败: " + e.getMessage());
            System.err.println("actionAddRoom 错误: " + e.getMessage());
        }
    }

    private void videoParsing(Message msg, Map<String, String> query) {
        try {
            String url = query.get("url");
            if (url == null) {
                throw new IllegalArgumentException("解析URl缺省！");
            }
            DouYinVideo parse = DouYinVideoParse.parse(url);
            msg.setData(parse);
            msg.setMessage("解析成功！");
        } catch (Exception e) {
            msg.setCode(1);
            msg.setMessage(e.getMessage());
        }
    }

    private boolean checkActionKey(Map<String, String> query, Message msg) {
        String actionKey = query.get("actionKey");
        if (StrUtil.isBlank(actionKey)) {
            msg.setMessage("操作秘钥不能为空！");
            return false;
        }
        if (!actionKey.equals(Constant.deviceUniqueId)) {
            msg.setMessage("操作秘钥错误！");
            return false;
        }
        return true;
    }

    private boolean checkRoomKey(Map<String, String> query, Message msg) {
        String key = query.get("key");
        if (StrUtil.isBlank(key)) {
            msg.setMessage("直播间标识不能为空！");
            return false;
        }
        RoomFileModel model = FileModeMain.getModelById(key);
        if (model == null) {
            msg.setCode(1);
            msg.setMessage("直播间标识[" + key + "]不存在！");
            return false;
        }
        return true;
    }
}
