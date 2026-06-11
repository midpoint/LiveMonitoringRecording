package cn.zhangheng.common.httpServer.auth;

import cn.hutool.core.util.StrUtil;
import cn.zhangheng.common.bean.Setting;
import lombok.extern.slf4j.Slf4j;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.*;

/**
 * 网页监控面板认证管理器
 * 管理会话token，支持密码验证和自动过期
 *
 * @author: midpoint
 * @date: 2026/06/11
 */
@Slf4j
public class AuthManager {
    private static volatile AuthManager instance;

    private final String password;
    private final long lockTimeoutMs;
    private final Map<String, Long> sessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupExecutor;
    private final SecureRandom random = new SecureRandom();

    private AuthManager(Setting setting) {
        this.password = setting.getWebPassword();
        this.lockTimeoutMs = setting.getLockTimeoutMin() * 60_000L;
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "AuthSessionCleanup");
            t.setDaemon(true);
            return t;
        });
        // 每30秒清理过期会话
        cleanupExecutor.scheduleAtFixedRate(this::cleanExpiredSessions, 30, 30, TimeUnit.SECONDS);
        log.info("网页面板认证已启用 (锁屏超时: {}分钟)", setting.getLockTimeoutMin());
    }

    public static synchronized void init(Setting setting) {
        if (instance == null) {
            instance = new AuthManager(setting);
        }
    }

    public static AuthManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("AuthManager 未初始化，请先调用 init()");
        }
        return instance;
    }

    /**
     * 是否启用了密码保护
     */
    public boolean isEnabled() {
        return StrUtil.isNotBlank(password);
    }

    /**
     * 验证密码，成功返回新token，失败返回null
     */
    public String login(String inputPassword) {
        if (!isEnabled() || !password.equals(inputPassword)) {
            return null;
        }
        String token = generateToken();
        sessions.put(token, System.currentTimeMillis());
        log.info("用户已登录，当前活跃会话数: {}", sessions.size());
        return token;
    }

    /**
     * 验证token是否有效，有效则刷新最后活动时间
     */
    public boolean verify(String token) {
        if (token == null) return false;
        Long lastActivity = sessions.get(token);
        if (lastActivity == null) return false;

        long now = System.currentTimeMillis();
        if (now - lastActivity > lockTimeoutMs) {
            // 已过期
            sessions.remove(token);
            return false;
        }

        // 刷新最后活动时间
        sessions.put(token, now);
        return true;
    }

    /**
     * 获取锁屏超时时间（毫秒），供前端读取
     */
    public long getLockTimeoutMs() {
        return lockTimeoutMs;
    }

    /**
     * 退出登录（移除token）
     */
    public void logout(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void cleanExpiredSessions() {
        long now = System.currentTimeMillis();
        int cleaned = 0;
        for (Map.Entry<String, Long> entry : sessions.entrySet()) {
            if (now - entry.getValue() > lockTimeoutMs) {
                sessions.remove(entry.getKey());
                cleaned++;
            }
        }
        if (cleaned > 0) {
            log.debug("清理了 {} 个过期会话", cleaned);
        }
    }
}
